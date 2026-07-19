// Fitness de performance (ADR-0027, GAP-AR/GAP-BO): jornada completa de simulação.
// Perfis via -e PROFILE=smoke|baseline|stress|soak|spike (default: smoke).
//
//   smoke    — 1 VU, 30s: valida o script e o ambiente (usado pelo workflow manual).
//   baseline — ramp 0→20 VUs, ~4min: mede p95/throughput contra o docker compose.
//   stress   — ramp 20→50→100 VUs, ~6min: acha o joelho (onde p95/erros disparam).
//   soak     — 20 VUs sustentados por ~34min: detecta leaks/degradação ao longo do tempo.
//   spike    — surto 10→100 VUs em 10s + recuperação: mede resiliência a picos súbitos.
//
// stress/spike relaxam os thresholds de latência de propósito (o valor é ONDE o p95
// estoura, não pass/fail); smoke/baseline/soak usam os thresholds do baseline vigente.
//
// Pré-requisitos:
//   1. Stack local com dev auth E confiança de proxy (para o XFF por-iteração virar
//      cliente distinto — pós-GAP-BL; sem isso o rate limit colapsa numa só cota):
//        JWT_DEV_MODE=true TRUSTED_PROXY_COUNT=1 GRAFANA_ADMIN_PASSWORD=admin docker compose up --build
//   2. Organização seedada (não há rota nem migration de criação de organização):
//        docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
//          "INSERT INTO organizations (id, name) VALUES
//           ('11111111-1111-4111-8111-111111111111', 'k6-load-org')
//           ON CONFLICT (id) DO NOTHING;"
//
//   k6 run -e PROFILE=baseline --summary-export=k6-summary.json load/simulation-journey.js
//
// Baseline vigente: docs/quality/performance-baseline-2026-07.md
// Sinal de regressão: scripts/perf-regression.sh <referencia.json> <k6-summary.json>

import http from 'k6/http';
import { check, group } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Thresholds do baseline vigente — sinal executável (ADR-0027): NÃO é gate de PR.
// Novo threshold exige nova medição documentada em docs/quality/ (ver Confirmation da ADR).
const baseThresholds = {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{endpoint:create}': ['p(95)<300'],
  'http_req_duration{endpoint:run_day}': ['p(95)<500'],
  // days: leitura GET (série do dia) da mesma classe de cfd/snapshot (p95 7–8ms no baseline
  // 2026-07, << 300ms; ver docs/quality/performance-baseline-2026-07-days.md). O threshold
  // materializa a submetric p/ o sinal de regressão (GAP-CR); o número empírico específico do
  // days vem do bootstrap da referência de CI (ADR-0039).
  'http_req_duration{endpoint:days}': ['p(95)<300'],
  'http_req_duration{endpoint:snapshot}': ['p(95)<300'],
  'http_req_duration{endpoint:cfd}': ['p(95)<300'],
  'http_req_duration{endpoint:list}': ['p(95)<300'],
};

// stress/spike: latência alta é esperada perto/no pico — thresholds relaxados para que o
// run não seja marcado "failed" indevidamente (o valor é ONDE degrada, não pass/fail).
const overloadThresholds = {
  http_req_failed: ['rate<0.05'],
  http_req_duration: ['p(95)<3000'],
};

const PROFILES = {
  smoke: {
    vus: 1,
    duration: '30s',
    thresholds: baseThresholds,
  },
  baseline: {
    stages: [
      { duration: '30s', target: 5 },
      { duration: '1m', target: 20 },
      { duration: '2m', target: 20 },
      { duration: '30s', target: 0 },
    ],
    thresholds: baseThresholds,
  },
  stress: {
    stages: [
      { duration: '1m', target: 20 },
      { duration: '2m', target: 50 },
      { duration: '2m', target: 100 },
      { duration: '1m', target: 0 },
    ],
    thresholds: overloadThresholds,
  },
  soak: {
    // Carga moderada sustentada: p95/erros estáveis ao longo do tempo = sem leak.
    stages: [
      { duration: '2m', target: 20 },
      { duration: '30m', target: 20 },
      { duration: '2m', target: 0 },
    ],
    thresholds: baseThresholds,
  },
  spike: {
    stages: [
      { duration: '30s', target: 10 },
      { duration: '10s', target: 100 },
      { duration: '1m', target: 100 },
      { duration: '10s', target: 10 },
      { duration: '1m', target: 10 },
      { duration: '10s', target: 0 },
    ],
    thresholds: overloadThresholds,
  },
};

const profileName = __ENV.PROFILE || 'smoke';
const profile = PROFILES[profileName];
if (!profile) {
  throw new Error(`PROFILE inválido: '${profileName}' — use ${Object.keys(PROFILES).join(' | ')}`);
}

export const options = {
  ...profile,
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const DAYS_TO_RUN = 5;

export function setup() {
  const organizationId = __ENV.ORG_ID || '11111111-1111-4111-8111-111111111111';
  const tokenRes = http.post(
    `${BASE_URL}/auth/token`,
    JSON.stringify({ subject: 'k6-load-test', organizationId }),
    { headers: JSON_HEADERS },
  );
  check(tokenRes, { 'auth token issued': (r) => r.status === 200 });
  if (tokenRes.status !== 200) {
    // Fail fast: sem token válido toda métrica seria de 401 — aborta com causa clara.
    exec.test.abort(`POST /auth/token respondeu ${tokenRes.status} — stack está de pé com JWT_DEV_MODE=true e organização seedada?`);
  }
  return { token: tokenRes.json('token'), organizationId };
}

export default function (data) {
  // Cada iteração simula um CLIENTE distinto: o rate limit da API (100 req/min) é chaveado
  // pelo IP real do cliente. Rode a API com TRUSTED_PROXY_COUNT=1 para que o único entry de
  // X-Forwarded-For enviado abaixo seja tratado como o cliente genuíno (com o default 0 a API
  // ignora o XFF e chaveia pelo peer do socket — todo o tráfego k6 colapsaria numa só cota).
  // Assim um XFF único por iteração mede a capacidade do servidor sob N clientes — não o
  // limitador (uma jornada tem ~9 requests, muito abaixo do limite por cliente).
  const it = exec.scenario.iterationInTest;
  const clientIp = `10.${(it >> 16) & 0xff}.${(it >> 8) & 0xff}.${it & 0xff}`;
  const auth = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${data.token}`,
    'X-Forwarded-For': clientIp,
  };

  let simulationId;

  group('create simulation', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/simulations`,
      JSON.stringify({
        organizationId: data.organizationId,
        wipLimit: 5,
        teamSize: 4,
        seedValue: 12345,
      }),
      { headers: auth, tags: { endpoint: 'create' } },
    );
    const created = check(res, { 'simulation created': (r) => r.status === 201 || r.status === 200 });
    // Só parseia em sucesso: erro pode vir sem corpo JSON e abortaria o teste inteiro.
    simulationId = created ? res.json('simulationId') : undefined;
  });

  if (!simulationId) return;

  let allDaysOk = true;
  group('run days', () => {
    for (let day = 0; day < DAYS_TO_RUN; day++) {
      const res = http.post(
        `${BASE_URL}/api/v1/simulations/${simulationId}/run`,
        JSON.stringify({ decisions: [] }),
        { headers: auth, tags: { endpoint: 'run_day' } },
      );
      if (!check(res, { 'day executed': (r) => r.status === 200 })) {
        allDaysOk = false;
        break;
      }
    }
  });

  // Dia falhou → queries produziriam 404/500 e contaminariam o p95 por endpoint;
  // a falha já foi registrada no check — encerra a iteração.
  if (!allDaysOk) return;

  // Invariantes de NEGÓCIO da jornada (não só status codes): validam o contrato
  // fim-a-fim sob carga. Regras profundas (WIP limit, ordenação Burrows,
  // determinismo por seed) vivem no domain + PITest — não duplicar aqui.
  // Parse protegido: corpo inválido/contrato quebrado deve FALHAR o check,
  // nunca lançar exceção (exceção aborta a iteração e mascara o invariante).
  const safeJson = (r) => {
    try {
      return r.json();
    } catch (_) {
      return undefined;
    }
  };

  group('query results', () => {
    const days = http.get(`${BASE_URL}/api/v1/simulations/${simulationId}/days`, {
      headers: auth,
      tags: { endpoint: 'days' },
    });
    const daysBody = safeJson(days);
    check(days, {
      'days listed': (r) => r.status === 200,
      [`days tem exatamente ${DAYS_TO_RUN} dias apos ${DAYS_TO_RUN} runs`]: (r) =>
        r.status === 200 && Array.isArray(daysBody && daysBody.days) && daysBody.days.length === DAYS_TO_RUN,
      'days pertence a simulacao criada': (r) =>
        r.status === 200 && daysBody !== undefined && daysBody.simulationId === simulationId,
    });

    const snapshot = http.get(`${BASE_URL}/api/v1/simulations/${simulationId}/days/1/snapshot`, {
      headers: auth,
      tags: { endpoint: 'snapshot' },
    });
    const snapshotBody = safeJson(snapshot);
    check(snapshot, {
      'snapshot returned': (r) => r.status === 200,
      'snapshot responde o dia pedido (day == 1)': (r) =>
        r.status === 200 && snapshotBody !== undefined && snapshotBody.day === 1,
      'snapshot pertence a simulacao criada': (r) =>
        r.status === 200 && snapshotBody !== undefined && snapshotBody.simulationId === simulationId,
    });

    const cfd = http.get(`${BASE_URL}/api/v1/simulations/${simulationId}/cfd`, {
      headers: auth,
      tags: { endpoint: 'cfd' },
    });
    const cfdBody = safeJson(cfd);
    check(cfd, {
      'cfd returned': (r) => r.status === 200,
      [`cfd tem serie consistente com os ${DAYS_TO_RUN} dias`]: (r) =>
        r.status === 200 && Array.isArray(cfdBody && cfdBody.series) && cfdBody.series.length === DAYS_TO_RUN,
    });

    const list = http.get(`${BASE_URL}/api/v1/simulations?organizationId=${data.organizationId}`, {
      headers: auth,
      tags: { endpoint: 'list' },
    });
    const listBody = safeJson(list);
    check(list, {
      'simulations listed': (r) => r.status === 200,
      // Estável sob concorrência (paginado): a org tem pelo menos a simulação
      // desta iteração e a página vem populada. "Contém o id criado" seria
      // flaky com 20 VUs criando em paralelo.
      'list da organizacao nao esta vazio': (r) =>
        r.status === 200 && listBody !== undefined && listBody.total >= 1 &&
        Array.isArray(listBody.data) && listBody.data.length > 0,
    });
  });
}
