# SKILLS_v1 — applicability audit for SceneDeck Android

Date: 2026-10-04. Inventory is a snapshot of every discovered installed SKILL.md under the supplied local/system/plugin roots plus `.kimi/skills`. **492 physical files / 489 unique names**. The orchestrator scanner excludes itself: its scan reports 491 files / 488 unique names; the complete table below includes its own SKILL.md as the 492nd file. Duplicate names remain listed by physical path.

Physical decision counts: **26 applied, 4 consulted/excluded, 81 related/covered,
381 excluded**. The table's row count, unique-name count and categories were checked
programmatically against these totals.

## Method and limits

- Enumerated all skill metadata before selection; fully read the user-supplied named skills and relevant active skill instructions, with required mini/catalog/review references for actual decisions. Metadata-only consideration is explicitly distinguished from activation below.
- Executed orchestrator scan/match/orchestration using imported installed scripts with DATA_DIR/REGISTRY/HASHES/PROJECTS paths redirected to `/tmp/scenedeck-skill-registry`. Installed scripts and registries were not modified. Matcher keywords are leads, not applicability proof.
- Explicitly requested orchestration/review skills shape this workflow. Three specialists work in owned areas; report references/checklists preserve memory; only one owner runs Gradle. No recurring task was requested and no scheduler was created.
- Native repository skills and current AGENTS/TECH_STACK/DESIGN_SYSTEM control Android details. Framework/backend/platform examples in broad skills do not justify changing the app stack.
- Duplicate generic checklists/tool-specific workflows are assessed as related but not separately activated when narrower applied guidance already covers the work. This report does not claim every installed skill body was loaded or every suggested external scanner executed.

## Fully consulted and applied guidance

| Skill | Application |
|---|---|
| [planning-and-task-breakdown](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/planning-and-task-breakdown/SKILL.md) | Concrete per-slice plan/checklist with acceptance, dependencies, human approval and final gate. |
| [agent-orchestrator](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-orchestrator/SKILL.md) | Registry scan, ranked matching and orchestration scripts executed with output paths redirected to /tmp; installed skills unchanged. |
| [agent-squad](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/SKILL.md) | Root coordinates three specialists; workers own application review/implementation/tests; versioned compressed report handoffs. |
| [agent-memory-systems](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-memory-systems/SKILL.md) | Persistent plan/checklist/versioned reports retrieved by path; conflicting findings retained with acceptance/deferral rationale. |
| [agent-orchestration-multi-agent-optimize](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-orchestration-multi-agent-optimize/SKILL.md) | Bounded concurrent domain ownership, single Gradle owner, deterministic baseline/work counts and event-driven handoffs. |
| [code-review-ai-ai-review](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review-ai-ai-review/SKILL.md) | Layered static scans plus contextual specialist review, independent proposal challenge and final diff review; no external messaging. |
| [android-compose](/home/worxbend/Projects/Github/scenedeck-android/.kimi/skills/android-compose/SKILL.md) | UDF, lifecycle collection, Kotlin/KSP/AGP pinned stack, stable lazy keys, appropriate tests and preserved module rules. |
| [material3-expressive](/home/worxbend/Projects/Github/scenedeck-android/.kimi/skills/material3-expressive/SKILL.md) | Review against current StudioChrome/Stylebook colors, motion/insets/semantics; synchronize stale guidance without visual redesign. |
| [obs-websocket-v5](/home/worxbend/Projects/Github/scenedeck-android/.kimi/skills/obs-websocket-v5/SKILL.md) | OBS boundary, bounded requests, cancellation, meter subscription, screenshot response and no replay controls. |
| [Kotlin Coroutines](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kotlin-coroutines/SKILL.md) | Structured jobs, cancellation propagation, awaited bounded work, conflated discovery and atomic publication. |
| [Kotlin Null Safety](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kotlin-null-safety/SKILL.md) | Typed errors/session readiness, nullable output states and safe unavailable-response handling at OBS boundary. |
| [api-and-interface-design](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-and-interface-design/SKILL.md) | Keep ObsClient contract stable, preserve cancellation/session semantics and avoid leaking implementation diagnostics. |
| [api-security](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-security/SKILL.md) | Defensive local trust-boundary/credential/error review; no remote target probing. |
| [clean-code](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/clean-code/SKILL.md) | Cohesive names/functions, explicit failure paths, small evidence-based changes and regression coverage. |
| [clean-code-guard](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/clean-code-guard/SKILL.md) | Generated diff self-check for SOLID/DRY/KISS/YAGNI, boundary validation, cancellation and no speculative abstractions. |
| [clean-code-principles](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/clean-code-principles/SKILL.md) | Module responsibilities, existing data access boundaries, elimination of repeated knowledge and scoped changes. |
| [architecture](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architecture/SKILL.md) | Existing product constraints and tradeoffs drive small changes; no layer redesign. |
| [architect-review](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architect-review/SKILL.md) | Review dependency direction and state/resource ownership across application/data/OBS boundaries. |
| [gof-patterns](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/gof-patterns/SKILL.md) | Full catalog reviewed; retain idiomatic Flow Observer, injected single process ownership and the real OBS adapter/facade seam. |
| [grasp-knowledge](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/grasp-knowledge/SKILL.md) | Portable Information Expert/Controller/Low Coupling/High Cohesion/Protected Variations; PHP-specific heuristics ignored. |
| [refactoring-guru](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/refactoring-guru/SKILL.md) | Required mini rules applied: diagnose smell, smallest treatment, semantic preservation and stop at scoped fix. |
| [safe-refactor](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/safe-refactor/SKILL.md) | Preserve ordering/default/merge/public interfaces with verification before/after structural edits. |
| [code-complete](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-complete/SKILL.md) | Required mini rules applied: construction clarity, explicit units/bounds, invariant/error policy and evidence-based tuning. |
| [code-review-excellence](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review-excellence/SKILL.md) | Severity/evidence/actionable review and independent peer challenge rather than style-only comments. |
| [performance-profiling](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/performance-profiling/SKILL.md) | Portable baseline→target→validate process for deterministic work/call counts; browser Lighthouse tooling not used. |
| [bash-linux](/home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/bash-linux/SKILL.md) | Local quality-command scope/quoting/fail-fast audit, pinned CLI/source validation and actual target evidence; retain existing script after disproving a misleading tracked-only summary. |

Required supporting references read: GoF `references/patterns.md` (full catalog), refactoring-guru mini, Code Complete mini, clean-code-guard AI failure modes/review checklist/SOLID/DRY-KISS-YAGNI. Kotlin/OBS/API references and pinned bytecode were reviewed by the corresponding specialist. Applied-source reports: SECURITY_OBS_v1, PERFORMANCE_DATA_v1 and QUALITY_UI_v1.

## Consulted but not activated as an implementation workflow

- **agent-self-scheduling** — Explicitly requested and read; recurring/scheduled work was not requested, so no timers/cron/loops installed. Use event-driven current-session agent handoffs only.
- **architecture-review** — Read and rejected: specifically requires Ontoly graph capabilities unavailable in this workspace; direct source/module review used.
- **architecture-patterns** — Read and rejected for implementation: backend/microservices migration skill excludes primarily frontend/local refactors; retain Android architecture.
- **mobile-design** — Read and assessed; generic RN/Flutter and pinning/font/icon defaults do not replace explicit native Android/OBS/design contract. Project mobile skills cover task.

## Related metadata assessed without separate activation

Generic review/security/performance/refactor/test/CI/documentation/API/database skills overlap the active native/project and evidence-review workflows above. No schema, dependency pins, CI delivery scripts, new API/backend or visual navigation feature is changed by this pass. Their extra framework/tool installation/migration instructions do not improve these localized accepted fixes. Activation remains tied to concrete work instead of keyword overlap. The physical inventory marks these cases `Related/covered`.

## Other-platform/tool exclusions

Lua/Nim/Scala/Go/PHP/ZIO/Effect/fp-ts/React/Flutter-specific implementation skills, AWS/Kubernetes/Docker/Render/Sites/Stripe/Supabase and document/spreadsheet/media/marketing/research/pet/app workflows do not match this native Kotlin OBS remote. No plugin installation, remote publishing, external messages, production probing, arbitrary host restriction or scheduling was authorized as part of this local improvement pass. Existing Python/Ruby delivery tooling was read for baseline audit but is unchanged; its language/tool skills remain metadata-considered supporting material. Bash/Linux guidance was fully read and applied to local quality-command coverage validation; the existing script was retained after inspecting actual targets.

## Complete physical metadata inventory

`Applied` = active instructions used. `Consulted/excluded` = body assessed but workflow excluded. `Related/covered` = related metadata, already covered by narrower active guidance or no matching changed subsystem. `Excluded` = no applicable implementation/review workflow in this task. Paths are provenance, not instructions to execute that skill.

| Name | Decision | Source |
|---|---|---|
| 12-factor-app | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/12-factor-app/SKILL.md |
| add-missing-section | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/add-missing-section/SKILL.md |
| agent-memory-systems | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-memory-systems/SKILL.md |
| agent-observability | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-observability/SKILL.md |
| agent-orchestration-improve-agent | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-orchestration-improve-agent/SKILL.md |
| agent-orchestration-multi-agent-optimize | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-orchestration-multi-agent-optimize/SKILL.md |
| agent-orchestrator | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-orchestrator/SKILL.md |
| agent-self-scheduling | Consulted/excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-self-scheduling/SKILL.md |
| agent-squad | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/SKILL.md |
| ai-analyzer | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ai-analyzer/SKILL.md |
| ai-engineer | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ai-engineer/SKILL.md |
| alex | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/alex/SKILL.md |
| ameba-configuration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ameba-configuration/SKILL.md |
| ameba-custom-rules | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ameba-custom-rules/SKILL.md |
| ameba-integration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ameba-integration/SKILL.md |
| analytics | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/analytics/SKILL.md |
| analytics-product | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/analytics-product/SKILL.md |
| analytics-tracking | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/analytics-tracking/SKILL.md |
| analyze-data-quality | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/analyze-data-quality/SKILL.md |
| analyze-performance | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/analyze-performance/SKILL.md |
| analyze-project | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/analyze-project/SKILL.md |
| andrej-karpathy | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/andrej-karpathy/SKILL.md |
| android-compose | Applied | /home/worxbend/Projects/Github/scenedeck-android/.kimi/skills/android-compose/SKILL.md |
| api-analyzer | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-analyzer/SKILL.md |
| api-and-interface-design | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-and-interface-design/SKILL.md |
| api-design-principles | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-design-principles/SKILL.md |
| api-designer | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-designer/SKILL.md |
| api-documentation | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-documentation/SKILL.md |
| api-documenter | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-documenter/SKILL.md |
| api-patterns | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-patterns/SKILL.md |
| api-security | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-security/SKILL.md |
| api-security-best-practices | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/api-security-best-practices/SKILL.md |
| architect-review | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architect-review/SKILL.md |
| architecting-data | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architecting-data/SKILL.md |
| architecting-networks | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architecting-networks/SKILL.md |
| architecting-security | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architecting-security/SKILL.md |
| architecture | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architecture/SKILL.md |
| architecture-patterns | Consulted/excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architecture-patterns/SKILL.md |
| architecture-review | Consulted/excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/architecture-review/SKILL.md |
| aria | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/aria/SKILL.md |
| artifact-template-analytics-dashboard | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-analytics-dashboard/SKILL.md |
| artifact-template-business-review | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-business-review/SKILL.md |
| artifact-template-design-report | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-design-report/SKILL.md |
| artifact-template-experiment-analysis | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-experiment-analysis/SKILL.md |
| artifact-template-financial-budget | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-financial-budget/SKILL.md |
| artifact-template-investment-committee-memo | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-investment-committee-memo/SKILL.md |
| artifact-template-legal-memorandum | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-legal-memorandum/SKILL.md |
| artifact-template-market-trends-report | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-market-trends-report/SKILL.md |
| artifact-template-minimal-letterhead | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-minimal-letterhead/SKILL.md |
| artifact-template-operating-calendar | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-operating-calendar/SKILL.md |
| artifact-template-operating-review | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-operating-review/SKILL.md |
| artifact-template-project-kickoff | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-project-kickoff/SKILL.md |
| artifact-template-project-tracker | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-project-tracker/SKILL.md |
| artifact-template-sales-pipeline | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-sales-pipeline/SKILL.md |
| artifact-template-simple-dark-mode | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-simple-dark-mode/SKILL.md |
| artifact-template-simple-light-mode | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-simple-light-mode/SKILL.md |
| artifact-template-strategy-memorandum | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-strategy-memorandum/SKILL.md |
| artifact-template-system-design | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-system-design/SKILL.md |
| artifact-template-team-alignment | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-team-alignment/SKILL.md |
| artifact-template-three-statement-forecast | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-three-statement-forecast/SKILL.md |
| ascii-diagram | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/ascii-diagram/SKILL.md |
| astro | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/astro/SKILL.md |
| audit-log | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/audit-log/SKILL.md |
| audit-logging | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/audit-logging/SKILL.md |
| audit-skills | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/audit-skills/SKILL.md |
| aws-ec2 | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/aws-ec2/SKILL.md |
| aws-iam | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/aws-iam/SKILL.md |
| aws-rds | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/aws-rds/SKILL.md |
| aws-s3 | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/aws-s3/SKILL.md |
| aws-skills | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/aws-skills/SKILL.md |
| backend-architect | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/backend-architect/SKILL.md |
| backend-dev-guidelines | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/backend-dev-guidelines/SKILL.md |
| backend-patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/backend-patterns/SKILL.md |
| backfill-metadata | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/backfill-metadata/SKILL.md |
| bash-linux | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/bash-linux/SKILL.md |
| bash-pro | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/bash-pro/SKILL.md |
| bdd-patterns | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/bdd-patterns/SKILL.md |
| bdd-principles | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/bdd-principles/SKILL.md |
| bdd-scenarios | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/bdd-scenarios/SKILL.md |
| browser-testing-with-devtools | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/browser-testing-with-devtools/SKILL.md |
| build-dashboard | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/build-dashboard/SKILL.md |
| build-report | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/build-report/SKILL.md |
| building-ai-chat | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/building-ai-chat/SKILL.md |
| building-ci-pipelines | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/building-ci-pipelines/SKILL.md |
| building-clis | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/building-clis/SKILL.md |
| building-forms | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/building-forms/SKILL.md |
| building-tables | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/building-tables/SKILL.md |
| built-in-browser | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/.trash/1790846766037-368647-Z09xyZ/built-in-browser/SKILL.md |
| cavecrew | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/cavecrew/SKILL.md |
| caveman | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman/SKILL.md |
| caveman-commit | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-commit/SKILL.md |
| caveman-compress | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-compress/SKILL.md |
| caveman-discover | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-discover/SKILL.md |
| caveman-evidence-review | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-evidence-review/SKILL.md |
| caveman-explore | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-explore/SKILL.md |
| caveman-help | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-help/SKILL.md |
| caveman-learn | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-learn/SKILL.md |
| caveman-manage | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-manage/SKILL.md |
| caveman-optimize | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-optimize/SKILL.md |
| caveman-review | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-review/SKILL.md |
| caveman-setup | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-setup/SKILL.md |
| caveman-stats | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/caveman-stats/SKILL.md |
| chrome-browser | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/.trash/1790846766037-368647-dpdwtH/chrome-browser/SKILL.md |
| ci-cd-and-automation | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ci-cd-and-automation/SKILL.md |
| clean-code | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/clean-code/SKILL.md |
| clean-code-guard | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/clean-code-guard/SKILL.md |
| clean-code-principles | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/clean-code-principles/SKILL.md |
| cloud-environment-runtime | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/cloud-environment/0.1.2/skills/runtime/SKILL.md |
| code-audit | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-audit/SKILL.md |
| code-complete | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-complete/SKILL.md |
| code-documentation-code-explain | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-documentation-code-explain/SKILL.md |
| code-documentation-doc-generate | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-documentation-doc-generate/SKILL.md |
| code-refactoring-refactor-clean | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-refactoring-refactor-clean/SKILL.md |
| code-refactoring-tech-debt | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-refactoring-tech-debt/SKILL.md |
| code-review | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review/SKILL.md |
| code-review-ai-ai-review | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review-ai-ai-review/SKILL.md |
| code-review-and-quality | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review-and-quality/SKILL.md |
| code-review-checklist | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review-checklist/SKILL.md |
| code-review-excellence | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review-excellence/SKILL.md |
| code-review-sensei | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-review-sensei/SKILL.md |
| code-reviewer | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-reviewer/SKILL.md |
| code-simplification | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-simplification/SKILL.md |
| code-simplifier | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/code-simplifier/SKILL.md |
| codebase-cleanup-refactor-clean | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/codebase-cleanup-refactor-clean/SKILL.md |
| comment | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/comment/SKILL.md |
| companion-examples | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/companion-examples/SKILL.md |
| computer-use | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/.trash/1790846766037-368647-3ydvSb/computer-use/SKILL.md |
| constraint-driven-development | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/constraint-driven-development/SKILL.md |
| context-engineering | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/context-engineering/SKILL.md |
| convert-to-doc | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/convert-to-doc/SKILL.md |
| convert-to-slides | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/convert-to-slides/SKILL.md |
| copywriting | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/copywriting/SKILL.md |
| create-data-context | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/create-data-context/SKILL.md |
| create-pet | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/work-pets/0.1.6/skills/create-pet/SKILL.md |
| creating-dashboards | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/creating-dashboards/SKILL.md |
| critique | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/critique/SKILL.md |
| cross-linker | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/cross-linker/SKILL.md |
| data-engineer | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/data-engineer/SKILL.md |
| data-engineering-data-pipeline | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/data-engineering-data-pipeline/SKILL.md |
| data-scientist | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/data-scientist/SKILL.md |
| data-type-ref-checklist | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/data-type-ref-checklist/SKILL.md |
| data-type-ref-structure | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/data-type-ref-structure/SKILL.md |
| database-architect | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/database-architect/SKILL.md |
| database-design | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/database-design/SKILL.md |
| datadog | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/datadog/SKILL.md |
| datadog-automation | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/datadog-automation/SKILL.md |
| debugging-and-error-recovery | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/debugging-and-error-recovery/SKILL.md |
| deep-research | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/.trash/1790846766037-368647-XoUrHV/deep-research/SKILL.md |
| dep | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/dep/SKILL.md |
| dependency-analysis | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/dependency-analysis/SKILL.md |
| dependency-scanning | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/dependency-scanning/SKILL.md |
| dependency-upgrade | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/dependency-upgrade/SKILL.md |
| deploying-applications | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/deploying-applications/SKILL.md |
| deploying-on-aws | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/deploying-on-aws/SKILL.md |
| deploying-on-azure | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/deploying-on-azure/SKILL.md |
| deploying-on-gcp | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/deploying-on-gcp/SKILL.md |
| deprecation-and-migration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/deprecation-and-migration/SKILL.md |
| design-kpis | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/design-kpis/SKILL.md |
| design-patterns | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/design-patterns/SKILL.md |
| designing-apis | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/designing-apis/SKILL.md |
| designing-sdks | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/designing-sdks/SKILL.md |
| docker-compose-basics | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/docker-compose-basics/SKILL.md |
| docker-compose-networking | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/docker-compose-networking/SKILL.md |
| docker-compose-production | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/docker-compose-production/SKILL.md |
| dockerfile-optimise | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/dockerfile-optimise/SKILL.md |
| docs | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/.trash/1790846766037-368647-xkWkpw/docs/SKILL.md |
| docs-add-missing-section | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-add-missing-section/SKILL.md |
| docs-ascii-diagram | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-ascii-diagram/SKILL.md |
| docs-backfill-metadata | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-backfill-metadata/SKILL.md |
| docs-check-compliance | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-check-compliance/SKILL.md |
| docs-companion-examples | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-companion-examples/SKILL.md |
| docs-cross-linker | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-cross-linker/SKILL.md |
| docs-data-type-list-members | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-data-type-list-members/SKILL.md |
| docs-data-type-ref | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-data-type-ref/SKILL.md |
| docs-document-pr | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-document-pr/SKILL.md |
| docs-enrich-section | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-enrich-section/SKILL.md |
| docs-examples | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-examples/SKILL.md |
| docs-find-documentation-gaps | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-find-documentation-gaps/SKILL.md |
| docs-generator | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/docs-generator/SKILL.md |
| docs-how-to-guide | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-how-to-guide/SKILL.md |
| docs-integrate | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-integrate/SKILL.md |
| docs-list-undocumented-prs | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-list-undocumented-prs/SKILL.md |
| docs-markdown-table | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-markdown-table/SKILL.md |
| docs-mdoc-conventions | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-mdoc-conventions/SKILL.md |
| docs-module-ref | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-module-ref/SKILL.md |
| docs-organize-reference-docs | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-organize-reference-docs/SKILL.md |
| docs-organize-types | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-organize-types/SKILL.md |
| docs-pr-subsection | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-pr-subsection/SKILL.md |
| docs-reduce-redundancy | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-reduce-redundancy/SKILL.md |
| docs-report-method-coverage | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-report-method-coverage/SKILL.md |
| docs-research | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-research/SKILL.md |
| docs-skill-retrospection | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-skill-retrospection/SKILL.md |
| docs-tutorial | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-tutorial/SKILL.md |
| docs-verify-compliance | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-verify-compliance/SKILL.md |
| docs-writing-style | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/docs-writing-style/SKILL.md |
| documentation-and-adrs | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/documentation-and-adrs/SKILL.md |
| documents | Excluded | /home/worxbend/.codex/plugins/cache/openai-primary-runtime/documents/26.905.11957/skills/documents/SKILL.md |
| docx | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/docx/SKILL.md |
| doubt-driven-development | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/doubt-driven-development/SKILL.md |
| effect-concurrency | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/effect-concurrency/SKILL.md |
| effect-core-patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/effect-core-patterns/SKILL.md |
| effect-dependency-injection | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/effect-dependency-injection/SKILL.md |
| effect-error-handling | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/effect-error-handling/SKILL.md |
| effect-resource-management | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/effect-resource-management/SKILL.md |
| effect-schema | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/effect-schema/SKILL.md |
| effect-testing | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/effect-testing/SKILL.md |
| enrich-section | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/enrich-section/SKILL.md |
| error-debugging-error-analysis | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/error-debugging-error-analysis/SKILL.md |
| error-debugging-error-trace | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/error-debugging-error-trace/SKILL.md |
| error-debugging-multi-agent-review | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/error-debugging-multi-agent-review/SKILL.md |
| error-diagnostics-error-analysis | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/error-diagnostics-error-analysis/SKILL.md |
| error-diagnostics-error-trace | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/error-diagnostics-error-trace/SKILL.md |
| excel-live-control | Excluded | /home/worxbend/.codex/plugins/cache/openai-primary-runtime/spreadsheets/26.905.11957/skills/excel-live-control/SKILL.md |
| find-skills | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/find-skills/SKILL.md |
| fp-async | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-async/SKILL.md |
| fp-backend | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-backend/SKILL.md |
| fp-data-transforms | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-data-transforms/SKILL.md |
| fp-either-ref | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-either-ref/SKILL.md |
| fp-errors | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-errors/SKILL.md |
| fp-option-ref | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-option-ref/SKILL.md |
| fp-pipe-ref | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-pipe-ref/SKILL.md |
| fp-pragmatic | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-pragmatic/SKILL.md |
| fp-react | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-react/SKILL.md |
| fp-refactor | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-refactor/SKILL.md |
| fp-taskeither-ref | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/fp-taskeither-ref/SKILL.md |
| frontend-app-builder | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/build-web-apps/0.1.2/skills/frontend-app-builder/SKILL.md |
| frontend-design | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/frontend-design/SKILL.md |
| frontend-dev-guidelines | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/frontend-dev-guidelines/SKILL.md |
| frontend-patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/frontend-patterns/SKILL.md |
| frontend-testing-debugging | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/build-web-apps/0.1.2/skills/frontend-testing-debugging/SKILL.md |
| frontend-ui-engineering | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/frontend-ui-engineering/SKILL.md |
| gather-business-context | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/gather-business-context/SKILL.md |
| generating-documentation | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/generating-documentation/SKILL.md |
| git-pushing | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/git-pushing/SKILL.md |
| git-storytelling-commit-messages | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/git-storytelling-commit-messages/SKILL.md |
| git-storytelling-commit-strategy | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/git-storytelling-commit-strategy/SKILL.md |
| git-workflow-and-versioning | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/git-workflow-and-versioning/SKILL.md |
| github | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/github/SKILL.md |
| github-actions | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/github-actions/SKILL.md |
| github-actions-advanced | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/github-actions-advanced/SKILL.md |
| github-actions-debugger | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/github-actions-debugger/SKILL.md |
| github-actions-templates | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/github-actions-templates/SKILL.md |
| gof-patterns | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/gof-patterns/SKILL.md |
| golang-benchmark | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-benchmark/SKILL.md |
| golang-cli | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-cli/SKILL.md |
| golang-code-style | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-code-style/SKILL.md |
| golang-concurrency | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-concurrency/SKILL.md |
| golang-context | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-context/SKILL.md |
| golang-continuous-integration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-continuous-integration/SKILL.md |
| golang-data-structures | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-data-structures/SKILL.md |
| golang-database | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-database/SKILL.md |
| golang-dependency-injection | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-dependency-injection/SKILL.md |
| golang-dependency-management | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-dependency-management/SKILL.md |
| golang-design-patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-design-patterns/SKILL.md |
| golang-documentation | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-documentation/SKILL.md |
| golang-error-handling | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-error-handling/SKILL.md |
| golang-google-wire | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-google-wire/SKILL.md |
| golang-gopls | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-gopls/SKILL.md |
| golang-graphql | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-graphql/SKILL.md |
| golang-grpc | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-grpc/SKILL.md |
| golang-how-to | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-how-to/SKILL.md |
| golang-lint | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-lint/SKILL.md |
| golang-modernize | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-modernize/SKILL.md |
| golang-naming | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-naming/SKILL.md |
| golang-observability | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-observability/SKILL.md |
| golang-performance | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-performance/SKILL.md |
| golang-pkg-go-dev | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-pkg-go-dev/SKILL.md |
| golang-popular-libraries | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-popular-libraries/SKILL.md |
| golang-project-layout | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-project-layout/SKILL.md |
| golang-refactoring | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-refactoring/SKILL.md |
| golang-safety | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-safety/SKILL.md |
| golang-samber-do | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-samber-do/SKILL.md |
| golang-samber-hot | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-samber-hot/SKILL.md |
| golang-samber-lo | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-samber-lo/SKILL.md |
| golang-samber-mo | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-samber-mo/SKILL.md |
| golang-samber-oops | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-samber-oops/SKILL.md |
| golang-samber-ro | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-samber-ro/SKILL.md |
| golang-samber-slog | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-samber-slog/SKILL.md |
| golang-security | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-security/SKILL.md |
| golang-spf13-cobra | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-spf13-cobra/SKILL.md |
| golang-spf13-viper | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-spf13-viper/SKILL.md |
| golang-stay-updated | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-stay-updated/SKILL.md |
| golang-stretchr-testify | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-stretchr-testify/SKILL.md |
| golang-structs-interfaces | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-structs-interfaces/SKILL.md |
| golang-swagger | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-swagger/SKILL.md |
| golang-testing | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-testing/SKILL.md |
| golang-troubleshooting | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-troubleshooting/SKILL.md |
| golang-uber-dig | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-uber-dig/SKILL.md |
| golang-uber-fx | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/golang-uber-fx/SKILL.md |
| google-workspace | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/google-workspace/SKILL.md |
| grafana-dashboards | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/grafana-dashboards/SKILL.md |
| grasp-knowledge | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/grasp-knowledge/SKILL.md |
| guiding-users | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/guiding-users/SKILL.md |
| hashicorp-vault | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/hashicorp-vault/SKILL.md |
| how-to-checklist | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/how-to-checklist/SKILL.md |
| how-to-structure | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/how-to-structure/SKILL.md |
| human-copywrite | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/human-copywrite/SKILL.md |
| idea-refine | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/idea-refine/SKILL.md |
| imagegen | Excluded | /home/worxbend/.codex/skills/.system/imagegen/SKILL.md |
| implementing-api-patterns | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/implementing-api-patterns/SKILL.md |
| implementing-compliance | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/implementing-compliance/SKILL.md |
| implementing-drag-drop | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/implementing-drag-drop/SKILL.md |
| implementing-gitops | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/implementing-gitops/SKILL.md |
| implementing-observability | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/implementing-observability/SKILL.md |
| implementing-realtime-sync | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/implementing-realtime-sync/SKILL.md |
| implementing-search-filter | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/implementing-search-filter/SKILL.md |
| import-memory | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/import-memory/SKILL.md |
| incremental-implementation | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/incremental-implementation/SKILL.md |
| index | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/index/SKILL.md |
| ingesting-data | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ingesting-data/SKILL.md |
| interview-me | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/interview-me/SKILL.md |
| java-concurrency | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/java-concurrency/SKILL.md |
| java-generics | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/java-generics/SKILL.md |
| java-pro | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/java-pro/SKILL.md |
| java-streams-api | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/java-streams-api/SKILL.md |
| javascript-pro | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/javascript-pro/SKILL.md |
| jupyter-notebooks | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/jupyter-notebooks/SKILL.md |
| Kotlin Coroutines | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kotlin-coroutines/SKILL.md |
| Kotlin DSL Patterns | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kotlin-dsl-patterns/SKILL.md |
| Kotlin Null Safety | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kotlin-null-safety/SKILL.md |
| kotlin-coroutines-expert | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kotlin-coroutines-expert/SKILL.md |
| kpi-reporting | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/kpi-reporting/SKILL.md |
| kubernetes-architect | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kubernetes-architect/SKILL.md |
| kubernetes-hardening | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/kubernetes-hardening/SKILL.md |
| load-balancing-patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/load-balancing-patterns/SKILL.md |
| Lua C Integration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/lua-c-integration/SKILL.md |
| Lua Coroutines | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/lua-coroutines/SKILL.md |
| Lua Tables Patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/lua-tables-patterns/SKILL.md |
| luna | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/luna/SKILL.md |
| maintain-space | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/pages/0.1.19/skills/maintain-space/SKILL.md |
| manage-schedules | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/pages/0.1.19/skills/manage-schedules/SKILL.md |
| managing-incidents | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/managing-incidents/SKILL.md |
| managing-media | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/managing-media/SKILL.md |
| markdown-table | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/markdown-table/SKILL.md |
| market-sizing | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/market-sizing/SKILL.md |
| mason | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/mason/SKILL.md |
| material3-expressive | Applied | /home/worxbend/Projects/Github/scenedeck-android/.kimi/skills/material3-expressive/SKILL.md |
| max | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/max/SKILL.md |
| mdoc-conventions | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/mdoc-conventions/SKILL.md |
| megacave | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/megacave/SKILL.md |
| metric-diagnostics | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/metric-diagnostics/SKILL.md |
| mobile-design | Consulted/excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/mobile-design/SKILL.md |
| module-ref-checklist | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/module-ref-checklist/SKILL.md |
| module-ref-structure | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/module-ref-structure/SKILL.md |
| module-subpages | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/module-subpages/SKILL.md |
| morning | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/morning/SKILL.md |
| Nim C Interop | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/nim-c-interop/SKILL.md |
| Nim Memory Management | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/nim-memory-management/SKILL.md |
| Nim Metaprogramming | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/nim-metaprogramming/SKILL.md |
| obs-websocket-v5 | Applied | /home/worxbend/Projects/Github/scenedeck-android/.kimi/skills/obs-websocket-v5/SKILL.md |
| observability-and-instrumentation | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/observability-and-instrumentation/SKILL.md |
| observability-cloud-planning | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/observability-cloud-planning/SKILL.md |
| observability-engineer | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/observability-engineer/SKILL.md |
| openai-docs | Excluded | /home/worxbend/.codex/skills/.system/openai-docs/SKILL.md |
| operating-kubernetes | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/operating-kubernetes/SKILL.md |
| organize-reference-docs | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/organize-reference-docs/SKILL.md |
| organize-space | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/pages/0.1.19/skills/organize-space/SKILL.md |
| pdf | Excluded | /home/worxbend/.codex/plugins/cache/openai-primary-runtime/pdf/26.905.11957/skills/pdf/SKILL.md |
| pdf | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/pdf/SKILL.md |
| performance-engineer | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/performance-engineer/SKILL.md |
| performance-engineering | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/performance-engineering/SKILL.md |
| performance-management | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/performance-management/SKILL.md |
| performance-optimization | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/performance-optimization/SKILL.md |
| performance-optimizer | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/performance-optimizer/SKILL.md |
| performance-profiling | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/performance-profiling/SKILL.md |
| pets | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/work-pets/0.1.6/skills/pets/SKILL.md |
| planning-and-task-breakdown | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/planning-and-task-breakdown/SKILL.md |
| platform-engineering | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/platform-engineering/SKILL.md |
| plugin-management | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/plugin-management/0.1.0/skills/plugin-management/SKILL.md |
| pptx | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/pptx/SKILL.md |
| Presentations | Excluded | /home/worxbend/.codex/plugins/cache/openai-primary-runtime/presentations/26.905.11957/skills/presentations/SKILL.md |
| product-business-analysis | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/product-business-analysis/SKILL.md |
| providing-feedback | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/providing-feedback/SKILL.md |
| publish-artifact-to-sites | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/publish-artifact-to-sites/SKILL.md |
| pulumi-components | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pulumi-components/SKILL.md |
| pulumi-stacks | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pulumi-stacks/SKILL.md |
| pydantic-ai | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pydantic-ai/SKILL.md |
| pylint-checkers | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pylint-checkers/SKILL.md |
| pylint-configuration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pylint-configuration/SKILL.md |
| pylint-integration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pylint-integration/SKILL.md |
| pytest-advanced | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pytest-advanced/SKILL.md |
| pytest-fixtures | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pytest-fixtures/SKILL.md |
| pytest-plugins | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/pytest-plugins/SKILL.md |
| python-async-patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/python-async-patterns/SKILL.md |
| python-data-classes | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/python-data-classes/SKILL.md |
| python-development | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/python-development/SKILL.md |
| python-patterns | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/python-patterns/SKILL.md |
| python-pro | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/python-pro/SKILL.md |
| python-type-system | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/python-type-system/SKILL.md |
| quinn | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/quinn/SKILL.md |
| react-best-practices | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/build-web-apps/0.1.2/skills/react-best-practices/SKILL.md |
| record-browser | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/codex-browser-recorder/0.4.1/skills/record-browser/SKILL.md |
| reduce-redundancy | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/reduce-redundancy/SKILL.md |
| refactor | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/refactor/SKILL.md |
| refactoring-guru | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/refactoring-guru/SKILL.md |
| render-background-workers | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-background-workers/SKILL.md |
| render-blueprints | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-blueprints/SKILL.md |
| render-cli | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-cli/SKILL.md |
| render-cron-jobs | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-cron-jobs/SKILL.md |
| render-debug | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-debug/SKILL.md |
| render-deploy | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-deploy/SKILL.md |
| render-disks | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-disks/SKILL.md |
| render-docker | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-docker/SKILL.md |
| render-domains | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-domains/SKILL.md |
| render-env-vars | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-env-vars/SKILL.md |
| render-keyvalue | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-keyvalue/SKILL.md |
| render-mcp | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-mcp/SKILL.md |
| render-migrate-from-heroku | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-migrate-from-heroku/SKILL.md |
| render-monitor | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-monitor/SKILL.md |
| render-networking | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-networking/SKILL.md |
| render-postgres | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-postgres/SKILL.md |
| render-private-services | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-private-services/SKILL.md |
| render-scaling | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-scaling/SKILL.md |
| render-static-sites | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-static-sites/SKILL.md |
| render-web-services | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-web-services/SKILL.md |
| render-workflows | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a624c56bfe081918f7544f7d58f6faf/1.0.1/skills/render-workflows/SKILL.md |
| report-to-pdf | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/report-to-pdf/SKILL.md |
| research | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/research/SKILL.md |
| review-agent | Excluded | /home/worxbend/.codex/skills/.system/review-agent/SKILL.md |
| rex | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/agent-squad/rex/SKILL.md |
| safe-refactor | Applied | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/safe-refactor/SKILL.md |
| saga-orchestration | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/saga-orchestration/SKILL.md |
| Scala Collections | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/scala-collections/SKILL.md |
| Scala Functional Patterns | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/scala-functional-patterns/SKILL.md |
| Scala Type System | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/scala-type-system/SKILL.md |
| scala-pro | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/scala-pro/SKILL.md |
| schedule-refresh-jobs | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/schedule-refresh-jobs/SKILL.md |
| security-and-hardening | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/security-and-hardening/SKILL.md |
| security-best-practices | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/security-best-practices/SKILL.md |
| security-hardening | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/security-hardening/SKILL.md |
| security-review | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/security-review/SKILL.md |
| senior-architect | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/senior-architect/SKILL.md |
| senior-backend | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/senior-backend/SKILL.md |
| senior-data-engineer | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/senior-data-engineer/SKILL.md |
| senior-data-scientist | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/senior-data-scientist/SKILL.md |
| senior-devops | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/senior-devops/SKILL.md |
| senior-qa | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/senior-qa/SKILL.md |
| senior-security | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/senior-security/SKILL.md |
| setup-skill-hook | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/documentation/skills/setup-skill-hook/SKILL.md |
| shadcn | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/build-web-apps/0.1.2/skills/shadcn-best-practices/SKILL.md |
| share-artifact-summary | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/share-artifact-summary/SKILL.md |
| shipping-and-launch | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/shipping-and-launch/SKILL.md |
| sites-building | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/sites/0.1.75/skills/sites-building/SKILL.md |
| sites-hosting | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/sites/0.1.75/skills/sites-hosting/SKILL.md |
| sites-mcp | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/sites/0.1.75/skills/sites-mcp/SKILL.md |
| sites-preview-troubleshooting | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/sites/0.1.75/skills/sites-preview-troubleshooting/SKILL.md |
| skill-creator | Excluded | /home/worxbend/.codex/skills/.system/skill-creator/SKILL.md |
| skill-creator | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/skill-creator/SKILL.md |
| skill-installer | Excluded | /home/worxbend/.codex/skills/.system/skill-installer/SKILL.md |
| skill-router | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/skill-router/SKILL.md |
| skill-seekers | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/skill-seekers/SKILL.md |
| skill-writer | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/skill-writer/SKILL.md |
| sofa | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a7f2f8bd57081919ffc6623a333942a/1.5.0/skills/sofa/SKILL.md |
| sofa-contribute | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a7f2f8bd57081919ffc6623a333942a/1.5.0/skills/sofa-contribute/SKILL.md |
| sofa-status | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/app-6a7f2f8bd57081919ffc6623a333942a/1.5.0/skills/sofa-status/SKILL.md |
| source-driven-development | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/source-driven-development/SKILL.md |
| spec-driven-development | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/spec-driven-development/SKILL.md |
| Spreadsheets | Excluded | /home/worxbend/.codex/plugins/cache/openai-primary-runtime/spreadsheets/26.905.11957/skills/spreadsheets/SKILL.md |
| stripe-best-practices | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/build-web-apps/0.1.2/skills/stripe-best-practices/SKILL.md |
| supabase-postgres-best-practices | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/build-web-apps/0.1.2/skills/supabase-best-practices/SKILL.md |
| telegram-bot-builder | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/telegram-bot-builder/SKILL.md |
| telegram-mini-app | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/telegram-mini-app/SKILL.md |
| template-creator | Excluded | /home/worxbend/.codex/plugins/cache/openai-primary-runtime/template-creator/26.905.11957/skills/template-creator/SKILL.md |
| test-driven-development | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/test-driven-development/SKILL.md |
| testing-strategies | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/testing-strategies/SKILL.md |
| theming-components | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/theming-components/SKILL.md |
| tutorial-checklist | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/tutorial-checklist/SKILL.md |
| tutorial-structure | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/tutorial-structure/SKILL.md |
| ultracave | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/ultracave/SKILL.md |
| update-pet | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/work-pets/0.1.6/skills/update-pet/SKILL.md |
| using-agent-skills | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/using-agent-skills/SKILL.md |
| using-document-databases | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/using-document-databases/SKILL.md |
| using-graph-databases | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/using-graph-databases/SKILL.md |
| using-message-queues | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/using-message-queues/SKILL.md |
| using-relational-databases | Related/covered | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/using-relational-databases/SKILL.md |
| using-timeseries-databases | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/using-timeseries-databases/SKILL.md |
| using-vector-databases | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/using-vector-databases/SKILL.md |
| validate-data | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/validate-data/SKILL.md |
| visualize | Excluded | /home/worxbend/.codex/plugins/cache/openai-bundled/visualize/1.0.41/skills/visualize/SKILL.md |
| visualize-data | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/data-analytics/1.0.11/skills/visualize-data/SKILL.md |
| visualizing-data | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/visualizing-data/SKILL.md |
| wiki-builder | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/wiki-builder/SKILL.md |
| wiki-changelog | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/wiki-changelog/SKILL.md |
| wiki-page-writer | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/wiki-page-writer/SKILL.md |
| write-page | Excluded | /home/worxbend/.codex/plugins/cache/openai-curated-remote/pages/0.1.19/skills/write-page/SKILL.md |
| writing-dockerfiles | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/writing-dockerfiles/SKILL.md |
| writing-style | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/flowrite/src/skills/writing-style/SKILL.md |
| xlsx | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/synced/34dde8d7-27ba-4afa-9ab0-661151283389_dd008c3b-3c50-4f25-aa7c-1ef0cac49f90/xlsx/SKILL.md |
| zio-http-knowledge | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/zio-skills/skills/zio-http-knowledge/SKILL.md |
| zio-knowledge | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/zio-skills/plugins/zio-skills/skills/zio-knowledge/SKILL.md |
| zio-reference | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/scala-zio-skills/.opencode/skills/zio-reference/SKILL.md |
| zio-reference | Excluded | /home/worxbend/.ubuntu-bootstrap/dotfiles/agents/skills/scala-zio-skills/skills/zio-reference/SKILL.md |
