<!--
PR 템플릿. 해당 없는 섹션은 지워도 된다.
커밋은 Conventional Commits (feat/fix/refactor/test/docs/chore/ci/build) 를 따른다.
-->

## 변경 요약

<!-- 무엇을, 왜 바꿨는지 1~3줄. -->

## 변경 유형

- [ ] feat (기능)
- [ ] fix (버그)
- [ ] refactor (동작 변화 없는 구조 개선)
- [ ] test
- [ ] docs
- [ ] chore / ci / build (인프라·빌드·파이프라인)

## 확인 (Checklist)

- [ ] `./gradlew test` 통과 (e2e 영향 시 `:e2e-tests:test` 도)
- [ ] ktlint(`ktlintCheck`) 통과
- [ ] 도메인 경계(Modulith) 위반 없음
- [ ] Helm 변경 시 `helm lint` + `helm template | kubeconform` 확인
- [ ] 워크플로 변경 시 `actionlint` 통과 / 외부 action 은 commit SHA 핀
- [ ] Dockerfile 변경 시 `hadolint` 통과
- [ ] 비밀/자격증명 평문 노출 없음 (회사 이메일·내부 도메인 포함)

## 관련 이슈

<!-- Closes #123 형태로 연결. 없으면 비워둔다. -->
