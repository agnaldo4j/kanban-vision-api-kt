argument-hint: "[environment task, e.g.: Dockerfile, k8s deployment (optional)]"
allowed-tools: Read, Grep, Glob, Bash
---
name: local-and-production-environment
description: >
  Configure e opere o ambiente local e de produção deste projeto com Docker, Podman,
  Minikube e Kubernetes. Use este skill ao criar o Dockerfile, docker-compose, manifestos
  Kubernetes, ou ao depurar o ambiente de execução. Cobre o caminho completo:
  build da imagem → execução local → Kubernetes local (Minikube) → produção (K8s).
  Complementa opentelemetry (stack de observabilidade) e adr (decisão de infraestrutura).
---

# Ambiente Local e Produção: Docker, Podman, Minikube e Kubernetes

> *"Build once, run anywhere."*
> — Docker

> *"Kubernetes is not a PaaS. It provides building blocks for building developer platforms."*
> — Kubernetes Documentation

---

## Estado Atual do Projeto

Nenhum arquivo de infraestrutura existe hoje:

```
❌ Dockerfile          — GAP-G: sem imagem Docker
❌ docker-compose.yml  — GAP-G: sem ambiente local containerizado
❌ k8s/                — sem manifestos Kubernetes para produção
```

A aplicação roda localmente via `./gradlew :http_api:run` com PostgreSQL externo.
Configuração via `application.conf` com override por variáveis de ambiente:

```
PORT=8080         (ou default 8080)
DATABASE_URL      jdbc:postgresql://localhost:5432/kanbanvision
DATABASE_USER     kanban
DATABASE_PASSWORD kanban
```

---

## I. Docker

### O que é

**Docker** é uma plataforma de containerização que empacota a aplicação e todas as suas
dependências numa unidade portável e reproduzível — a **imagem**. Containers são
instâncias em execução de uma imagem, isolados do sistema operacional host.

Componentes:
- **Daemon (`dockerd`)**: processo que gerencia imagens, containers, volumes e redes
- **CLI (`docker`)**: cliente que envia comandos ao daemon via REST API
- **Registry**: armazena imagens (Docker Hub, ECR, GCR, GHCR)

### Dockerfile Multi-Stage para Kotlin/Ktor + OTel Agent

Multi-stage build: o estágio de build compila o JAR; o estágio de runtime é enxuto
(sem JDK, sem fontes, sem Gradle) — imagem final ~200MB em vez de ~600MB.

```dockerfile
# Dockerfile
# ─────────────────────────────── Estágio 1: Build ───────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copia os arquivos de configuração do Gradle primeiro (aproveita cache de camadas)
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/

# Baixa dependências (camada cacheada enquanto build files não mudam)
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# Copia o código-fonte
COPY domain/ domain/
COPY usecases/ usecases/
COPY sql_persistence/ sql_persistence/
COPY http_api/ http_api/
COPY config/ config/

# Compila e gera o fat JAR (sem testes — testes rodam no CI)
RUN ./gradlew :http_api:buildFatJar --no-daemon -x test -x detekt -x ktlintCheck

# ─────────────────────────────── Estágio 2: Runtime ──────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# Versão fixada do OTel Java Agent — nunca use "latest" para builds reproduzíveis
ARG OTEL_AGENT_VERSION=2.12.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar \
    /opt/opentelemetry-javaagent.jar

# Usuário não-root (segurança)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

WORKDIR /app

COPY --from=builder /build/http_api/build/libs/kanban-vision-api.jar app.jar

# Variáveis de ambiente com valores padrão sobrescríveis
ENV PORT=8080
ENV LOG_FORMAT=json
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/opentelemetry-javaagent.jar"

# Documentação das portas expostas
EXPOSE 8080

# Health check do Docker (complementa o /health/ready do Kubernetes)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/health/ready || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### .dockerignore

```
.gradle/
build/
**/build/
.git/
.github/
*.md
.claude/
adr/
config/detekt/
```

### Comandos essenciais

```bash
# Build da imagem
docker build -t kanban-vision-api:local .

# Build com versão do OTel Agent específica
docker build --build-arg OTEL_AGENT_VERSION=2.12.0 -t kanban-vision-api:1.0.0 .

# Executar container isolado
docker run --rm -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/kanbanvision \
  -e DATABASE_USER=kanban \
  -e DATABASE_PASSWORD=kanban \
  kanban-vision-api:local

# Inspecionar camadas da imagem (otimização de tamanho)
docker history kanban-vision-api:local

# Verificar tamanho final
docker images kanban-vision-api

# Entrar no container para debug
docker exec -it <container_id> sh
```

---

## II. Docker Compose — Ambiente Local

### docker-compose.yml (desenvolvimento)

Ambiente mínimo: aplicação + PostgreSQL.

```yaml
# docker-compose.yml
services:
  kanban-api:
    build:
      context: .
      dockerfile: Dockerfile
    image: kanban-vision-api:local
    ports:
      - "8080:8080"
    environment:
      PORT: 8080
      LOG_FORMAT: text                    # texto legível em desenvolvimento
      DATABASE_URL: jdbc:postgresql://postgres:5432/kanbanvision
      DATABASE_USER: kanban
      DATABASE_PASSWORD: kanban
      OTEL_METRICS_EXPORTER: none         # desliga OTel em dev básico
      OTEL_TRACES_EXPORTER: none
      OTEL_LOGS_EXPORTER: none
    depends_on:
      postgres:
        condition: service_healthy        # aguarda o banco estar saudável
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/health/ready"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: kanbanvision
      POSTGRES_USER: kanban
      POSTGRES_PASSWORD: kanban
    ports:
      - "5432:5432"                       # expõe para ferramentas locais (DBeaver, etc.)
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U kanban -d kanbanvision"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

volumes:
  postgres_data:
```

### docker-compose.observability.yml (stack de observabilidade)

Sobrepõe o `docker-compose.yml` para adicionar a stack completa de observabilidade.
Referência: ver skill `opentelemetry` para detalhes do otel-collector e Grafana.

```yaml
# docker-compose.observability.yml
services:
  kanban-api:
    environment:
      LOG_FORMAT: json
      JAVA_TOOL_OPTIONS: "-javaagent:/opt/opentelemetry-javaagent.jar"
      OTEL_SERVICE_NAME: kanban-vision-api
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
      OTEL_EXPORTER_OTLP_PROTOCOL: grpc
      OTEL_TRACES_SAMPLER: parentbased_traceidratio
      OTEL_TRACES_SAMPLER_ARG: "1.0"
      OTEL_METRICS_EXPORTER: none        # métricas via Micrometer/Prometheus
      OTEL_LOGS_EXPORTER: none           # logs via logstash-logback-encoder → Loki
    depends_on:
      otel-collector:
        condition: service_started

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.119.0
    volumes:
      - ./config/otel-collector.yml:/etc/otel/config.yml
    command: ["--config=/etc/otel/config.yml"]
    depends_on:
      - tempo
      - loki

  prometheus:
    image: prom/prometheus:v3.1.0
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  loki:
    image: grafana/loki:3.3.2
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml

  tempo:
    image: grafana/tempo:2.7.0
    command: ["-config.file=/etc/tempo/config.yml"]
    volumes:
      - ./config/tempo.yml:/etc/tempo/config.yml

  grafana:
    image: grafana/grafana:11.4.0
    ports:
      - "3000:3000"
    environment:
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Admin
    volumes:
      - ./config/grafana/datasources:/etc/grafana/provisioning/datasources
    depends_on:
      - prometheus
      - loki
      - tempo
```

### Comandos Docker Compose

```bash
# ── Ambiente básico ────────────────────────────────────────────────
# Subir (build automático se imagem não existir)
docker compose up -d

# Subir com rebuild forçado
docker compose up -d --build

# Ver logs em tempo real
docker compose logs -f kanban-api

# Verificar status dos containers
docker compose ps

# Reiniciar apenas a aplicação (sem recriar o banco)
docker compose restart kanban-api

# Parar e remover containers (preserva volumes)
docker compose down

# Parar e remover tudo, inclusive volumes (reset completo)
docker compose down -v

# ── Ambiente com observabilidade ──────────────────────────────────
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d

# ── Debug ──────────────────────────────────────────────────────────
# Verificar health check da API
curl -s http://localhost:8080/health/ready | jq

# Verificar métricas
curl -s http://localhost:8080/metrics | grep kanban

# Acessar Grafana: http://localhost:3000
# Acessar Prometheus: http://localhost:9090
# Acessar Swagger: http://localhost:8080/swagger
```

---

## III. Podman — Alternativa Rootless

### O que é e por que usar

**Podman** é uma alternativa ao Docker com arquitetura **daemonless** (sem processo
daemon rodando em background) e **rootless** por padrão (containers não precisam de
privilégios root no host — mais seguro).

| Característica | Docker | Podman |
|---|---|---|
| Arquitetura | Daemon centralizado | Daemonless (fork/exec) |
| Segurança | Root por padrão | Rootless por padrão |
| CLI | `docker` | `podman` (comandos idênticos) |
| Compose | Docker Compose | Podman Compose ou `podman-compose` |
| macOS | Docker Desktop | Podman Desktop |
| CI/CD | Padrão | Suportado em GitLab, GitHub Actions |

### Drop-in replacement

A CLI do Podman é 100% compatível com a do Docker. Basta criar um alias:

```bash
alias docker=podman
alias docker-compose=podman-compose
```

Todos os comandos deste skill funcionam sem alteração.

### Instalação no macOS

```bash
# Via Homebrew
brew install podman podman-compose

# Inicializar a VM (necessária no macOS — sem daemon nativo)
podman machine init --cpus 2 --memory 4096 --disk-size 30
podman machine start

# Verificar
podman version
podman info
```

### Diferenças relevantes para este projeto

```bash
# Podman não usa "docker.io" por padrão — especifique o registry
podman build -t kanban-vision-api:local .

# Para acessar o host a partir do container (equivalente ao host.docker.internal do Docker)
# Use o IP da interface podman (gateway da rede)
podman network inspect podman | grep -i gateway

# Rootless: portas < 1024 requerem configuração extra
# Porta 8080 funciona normalmente
```

---

## IV. Minikube — Kubernetes Local

### O que é

Minikube cria um cluster Kubernetes single-node na sua máquina local. Permite
desenvolver e testar manifestos Kubernetes sem acesso a um cluster remoto.

### Pré-requisitos

- 2+ CPUs, 4GB+ RAM livres, 20GB+ de disco
- Docker ou Podman instalado e rodando

### Instalação e setup

```bash
# macOS via Homebrew
brew install minikube kubectl

# Iniciar cluster (usa Docker como driver por padrão)
minikube start --driver=docker --cpus=2 --memory=4096 --disk-size=30g

# Verificar status
minikube status
kubectl cluster-info

# Habilitar addons essenciais
minikube addons enable ingress          # NGINX Ingress Controller
minikube addons enable metrics-server   # métricas para HPA
minikube addons enable dashboard        # UI do Kubernetes

# Acessar dashboard
minikube dashboard
```

### Usar imagem local no Minikube

O Minikube tem seu próprio registry Docker. Para usar a imagem local sem fazer push:

```bash
# Opção 1: apontar o Docker CLI para o daemon do Minikube
eval $(minikube docker-env)
docker build -t kanban-vision-api:local .
# Usar imagePullPolicy: Never nos manifestos

# Opção 2: carregar imagem diretamente
docker build -t kanban-vision-api:local .
minikube image load kanban-vision-api:local
```

### Deploy rápido no Minikube

```bash
# Aplicar todos os manifestos do diretório k8s/
kubectl apply -f k8s/

# Acompanhar rollout
kubectl rollout status deployment/kanban-vision-api -n kanban

# Acessar o serviço localmente
minikube service kanban-vision-api -n kanban --url

# Port-forward direto (alternativa ao Ingress em dev)
kubectl port-forward svc/kanban-vision-api 8080:80 -n kanban

# Logs da aplicação
kubectl logs -f deployment/kanban-vision-api -n kanban

# Limpar tudo
kubectl delete -f k8s/
```

---

## V. Kubernetes — Manifestos de Produção

Estrutura recomendada de arquivos:

```
k8s/
  namespace.yml
  configmap.yml
  secret.yml           ← não versionar valores reais; use Sealed Secrets ou External Secrets
  deployment.yml
  service.yml
  ingress.yml
  hpa.yml
  pdb.yml
```

### namespace.yml

```yaml
# k8s/namespace.yml
apiVersion: v1
kind: Namespace
metadata:
  name: kanban
  labels:
    app.kubernetes.io/name: kanban-vision-api
```

### configmap.yml

ConfigMap para configuração não-sensível:

```yaml
# k8s/configmap.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: kanban-vision-api-config
  namespace: kanban
data:
  PORT: "8080"
  LOG_FORMAT: "json"
  OTEL_SERVICE_NAME: "kanban-vision-api"
  OTEL_EXPORTER_OTLP_ENDPOINT: "http://otel-collector.observability:4317"
  OTEL_EXPORTER_OTLP_PROTOCOL: "grpc"
  OTEL_TRACES_SAMPLER: "parentbased_traceidratio"
  OTEL_TRACES_SAMPLER_ARG: "0.1"          # 10% de sampling em produção
  OTEL_METRICS_EXPORTER: "none"
  OTEL_LOGS_EXPORTER: "none"
```

### secret.yml

⚠️ **Nunca versione credenciais reais**. O arquivo abaixo é um template — em produção,
use [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) ou
[External Secrets Operator](https://external-secrets.io/) integrado ao seu Vault/SSM.

```yaml
# k8s/secret.yml  ← TEMPLATE — substituir valores antes do apply
apiVersion: v1
kind: Secret
metadata:
  name: kanban-vision-api-secrets
  namespace: kanban
type: Opaque
stringData:                               # stringData aceita texto; K8s converte para base64
  DATABASE_URL: "jdbc:postgresql://postgres-svc:5432/kanbanvision"
  DATABASE_USER: "kanban"
  DATABASE_PASSWORD: "SUBSTITUIR_EM_PRODUCAO"
```

### deployment.yml

```yaml
# k8s/deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kanban-vision-api
  namespace: kanban
  labels:
    app: kanban-vision-api
    version: "1.0.0"
spec:
  replicas: 2
  selector:
    matchLabels:
      app: kanban-vision-api
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1           # 1 pod extra durante update
      maxUnavailable: 0     # nenhum pod indisponível durante update (zero-downtime)
  template:
    metadata:
      labels:
        app: kanban-vision-api
        version: "1.0.0"
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: default
      securityContext:
        runAsNonRoot: true          # nunca rodar como root
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: kanban-vision-api
          image: ghcr.io/SEU_ORG/kanban-vision-api:1.0.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              name: http

          # Variáveis não-sensíveis do ConfigMap
          envFrom:
            - configMapRef:
                name: kanban-vision-api-config

          # Variáveis sensíveis do Secret
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: kanban-vision-api-secrets
                  key: DATABASE_URL
            - name: DATABASE_USER
              valueFrom:
                secretKeyRef:
                  name: kanban-vision-api-secrets
                  key: DATABASE_USER
            - name: DATABASE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: kanban-vision-api-secrets
                  key: DATABASE_PASSWORD
            - name: JAVA_TOOL_OPTIONS
              value: "-javaagent:/opt/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0"

          # ── Probes ─────────────────────────────────────────────────────────────
          # Startup: aguarda a JVM inicializar (30s max)
          startupProbe:
            httpGet:
              path: /health/live
              port: 8080
            failureThreshold: 10        # 10 tentativas × 3s = 30s máximo
            periodSeconds: 3

          # Liveness: reinicia o container se travar (sem verificar dependências)
          livenessProbe:
            httpGet:
              path: /health/live
              port: 8080
            initialDelaySeconds: 0      # startupProbe garante a inicialização
            periodSeconds: 10
            failureThreshold: 3
            timeoutSeconds: 5

          # Readiness: remove do load balancer se o banco estiver fora
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8080
            initialDelaySeconds: 0
            periodSeconds: 10
            failureThreshold: 3
            timeoutSeconds: 5

          # ── Recursos ────────────────────────────────────────────────────────────
          resources:
            requests:
              cpu: "250m"               # 0.25 vCPU garantido
              memory: "256Mi"           # 256MB garantido
            limits:
              cpu: "1000m"              # max 1 vCPU
              memory: "512Mi"           # max 512MB (JVM usa ~200MB + headroom)

      # Distribui pods entre nós disponíveis (alta disponibilidade)
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              app: kanban-vision-api
```

### service.yml

```yaml
# k8s/service.yml
apiVersion: v1
kind: Service
metadata:
  name: kanban-vision-api
  namespace: kanban
  labels:
    app: kanban-vision-api
spec:
  type: ClusterIP                       # interno ao cluster; exposto pelo Ingress
  selector:
    app: kanban-vision-api
  ports:
    - name: http
      port: 80
      targetPort: 8080
      protocol: TCP
```

### ingress.yml

```yaml
# k8s/ingress.yml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: kanban-vision-api
  namespace: kanban
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: letsencrypt-prod   # TLS automático via cert-manager
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - api.kanbanvision.io
      secretName: kanban-vision-api-tls
  rules:
    - host: api.kanbanvision.io
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: kanban-vision-api
                port:
                  number: 80
```

### hpa.yml — Autoescala horizontal

```yaml
# k8s/hpa.yml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: kanban-vision-api
  namespace: kanban
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: kanban-vision-api
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70        # escala quando CPU média > 70%
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80        # escala quando memória média > 80%
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30   # aguarda 30s antes de escalar para cima
    scaleDown:
      stabilizationWindowSeconds: 300  # aguarda 5min antes de reduzir réplicas
```

### pdb.yml — Protege contra interrupções

```yaml
# k8s/pdb.yml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: kanban-vision-api
  namespace: kanban
spec:
  minAvailable: 1                       # garante ao menos 1 pod UP durante node drain/updates
  selector:
    matchLabels:
      app: kanban-vision-api
```

---

## VI. Comandos kubectl Essenciais

```bash
# ── Contextos e namespaces ─────────────────────────────────────────
kubectl config get-contexts                          # listar clusters configurados
kubectl config use-context minikube                  # trocar para cluster local
kubectl config set-context --current --namespace=kanban  # namespace padrão

# ── Deploy e rollout ───────────────────────────────────────────────
kubectl apply -f k8s/                                # aplica todos os manifestos
kubectl rollout status deployment/kanban-vision-api  # acompanha o deploy
kubectl rollout history deployment/kanban-vision-api # histórico de versões
kubectl rollout undo deployment/kanban-vision-api    # rollback para versão anterior

# ── Observar estado ────────────────────────────────────────────────
kubectl get pods -n kanban -w                        # watch em tempo real
kubectl get pods -n kanban -o wide                   # mostra nós e IPs
kubectl describe pod <pod-name> -n kanban            # detalhes + eventos
kubectl top pods -n kanban                           # uso de CPU/memória

# ── Logs ───────────────────────────────────────────────────────────
kubectl logs -f deployment/kanban-vision-api -n kanban          # logs em tempo real
kubectl logs -f deployment/kanban-vision-api -n kanban --tail=100
kubectl logs <pod-name> -n kanban --previous          # logs do container anterior (crash)

# ── Debug ──────────────────────────────────────────────────────────
kubectl exec -it <pod-name> -n kanban -- sh           # shell no container
kubectl port-forward svc/kanban-vision-api 8080:80 -n kanban  # acesso local
kubectl get events -n kanban --sort-by='.lastTimestamp'        # eventos recentes

# ── Scaling manual ────────────────────────────────────────────────
kubectl scale deployment/kanban-vision-api --replicas=3 -n kanban

# ── Atualizar imagem (deploy nova versão) ─────────────────────────
kubectl set image deployment/kanban-vision-api \
  kanban-vision-api=ghcr.io/SEU_ORG/kanban-vision-api:1.1.0 -n kanban

# ── Verificar health checks ────────────────────────────────────────
kubectl get endpoints kanban-vision-api -n kanban    # pods no load balancer
```

---

## VII. Fluxo Completo de Desenvolvimento

```
1. Código alterado no IDE
       ↓
2. ./gradlew :http_api:buildFatJar   ← build local (mais rápido para dev)
   OU
   docker compose up -d --build      ← build + start containerizado
       ↓
3. Verificar:
   curl localhost:8080/health/ready
   curl localhost:8080/swagger
       ↓
4. Subir observabilidade (opcional):
   docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
   # Grafana: http://localhost:3000
       ↓
5. Testar no Minikube (valida manifestos K8s antes do CI):
   eval $(minikube docker-env)
   docker build -t kanban-vision-api:local .
   kubectl apply -f k8s/
   minikube service kanban-vision-api -n kanban --url
       ↓
6. Push → CI/CD → build imagem → push para registry → deploy em produção
```

---

## VIII. Checklist

### Dockerfile

- [ ] Multi-stage build (builder JDK + runtime JRE)?
- [ ] Usuário não-root (`runAsNonRoot: true` / `USER appuser`)?
- [ ] OTel Java Agent com versão fixada (não `latest`)?
- [ ] `.dockerignore` exclui `.git/`, `build/`, `*.md`?
- [ ] `HEALTHCHECK` instrução presente?
- [ ] Imagem final < 300MB?

### Docker Compose local

- [ ] `depends_on` com `condition: service_healthy` para aguardar o banco?
- [ ] `postgres` tem `healthcheck` com `pg_isready`?
- [ ] Volumes nomeados para dados persistentes?
- [ ] `LOG_FORMAT=text` em dev, `json` no compose de observabilidade?
- [ ] `docker compose down` não destrói dados (sem `-v` acidental)?

### Kubernetes

- [ ] Namespace dedicado (`kanban`)?
- [ ] `startupProbe` configurado para dar tempo à JVM inicializar?
- [ ] `livenessProbe` aponta para `/health/live` (sem checar dependências)?
- [ ] `readinessProbe` aponta para `/health/ready` (verifica PostgreSQL)?
- [ ] `resources.requests` e `resources.limits` definidos?
- [ ] `maxUnavailable: 0` no rolling update (zero-downtime)?
- [ ] `PodDisruptionBudget` com `minAvailable: 1`?
- [ ] `HorizontalPodAutoscaler` com `minReplicas: 2`?
- [ ] Credenciais em `Secret`, nunca em `ConfigMap`?
- [ ] Secrets reais **não versionados** no git (usar Sealed Secrets ou External Secrets)?
- [ ] `topologySpreadConstraints` para distribuir pods entre nós?
- [ ] Annotations `prometheus.io/scrape` no Deployment?

### Segurança

- [ ] Container roda como usuário não-root?
- [ ] `runAsNonRoot: true` no `securityContext` do Pod?
- [ ] Secrets não expostos em logs ou variáveis visíveis via `kubectl describe`?
- [ ] Imagem baseada em `eclipse-temurin:21-jre` (não `latest`, não `jdk` em runtime)?
- [ ] Registry de imagens com scanning de vulnerabilidades (Trivy, Snyk)?

---

## Referências

- Docker. *Docker Overview*. https://docs.docker.com/get-started/docker-overview/
- Docker. *Dockerfile reference*. https://docs.docker.com/reference/dockerfile/
- Docker. *Compose features and use cases*. https://docs.docker.com/compose/intro/features-uses/
- Podman. *Documentation*. https://podman.io/docs
- Minikube. *Getting Started*. https://minikube.sigs.k8s.io/docs/start/
- Kubernetes. *Documentation*. https://kubernetes.io/pt-br/docs/home/
- Kubernetes. *Configure Liveness, Readiness and Startup Probes*. https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- Kubernetes. *HorizontalPodAutoscaler*. https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/
- eclipse-temurin. *Docker Hub*. https://hub.docker.com/_/eclipse-temurin
- Skill: [opentelemetry](.claude/skills/opentelemetry/SKILL.md)