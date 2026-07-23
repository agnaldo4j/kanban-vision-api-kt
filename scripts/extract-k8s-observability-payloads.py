#!/usr/bin/env python3
#
# extract-k8s-observability-payloads.py — extrai os payloads de ConfigMap das cópias k8s de
# observabilidade para /tmp, de modo que o job `config-lint` possa validá-los com amtool/promtool
# (GAP-DB). Sem isto o `config-lint` só cobre `observability/*`, e um erro de schema nas cópias k8s
# (payloads de ConfigMap opacos ao kustomize) passaria o gate — o buraco que 3 reviews apontaram.
#
# - k8s/11-prometheus-rules.yml   → data["kanban-vision-alerts.yml"] → /tmp/k8s-rules.yml
# - k8s/12-prometheus-config.yml  → data["prometheus.yml"]           → /tmp/k8s-prom.yml
#   Os caminhos de auth in-cluster (bearer_token_file/ca_file, que o `promtool check config` exige
#   EXISTIR) são repontados para stubs em /tmp — a config in-cluster continua íntegra no repo; só a
#   validação local/CI usa os stubs.

import pathlib
import sys

try:
    import yaml
except ModuleNotFoundError:
    print("error: PyYAML not installed — run `pip install pyyaml`", file=sys.stderr)
    sys.exit(2)

ROOT = pathlib.Path(__file__).resolve().parent.parent


def first_doc(rel_path):
    docs = [d for d in yaml.safe_load_all((ROOT / rel_path).read_text()) if d]
    if not docs:
        raise SystemExit(f"error: no YAML document in {rel_path}")
    return docs[0]


def main():
    rules_cm = first_doc("k8s/11-prometheus-rules.yml")
    rules = rules_cm["data"]["kanban-vision-alerts.yml"]
    pathlib.Path("/tmp/k8s-rules.yml").write_text(rules)

    scrape_cm = first_doc("k8s/12-prometheus-config.yml")
    scrape = scrape_cm["data"]["prometheus.yml"]
    scrape = scrape.replace(
        "/var/run/secrets/kubernetes.io/serviceaccount/token", "/tmp/k8s-sa-token"
    ).replace(
        "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt", "/tmp/k8s-sa-ca.crt"
    )
    pathlib.Path("/tmp/k8s-prom.yml").write_text(scrape)
    pathlib.Path("/tmp/k8s-sa-token").write_text("stub-token")
    pathlib.Path("/tmp/k8s-sa-ca.crt").write_text("stub-ca")

    print("extracted: /tmp/k8s-rules.yml, /tmp/k8s-prom.yml (+ stub sa token/ca)")


if __name__ == "__main__":
    main()
