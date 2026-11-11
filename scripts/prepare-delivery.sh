#!/usr/bin/env bash
set -euo pipefail

# Run from the project root. Supply your real identity and private repository URL.
: "${GIT_AUTHOR_NAME:?Define GIT_AUTHOR_NAME con tu nombre real}"
: "${GIT_AUTHOR_EMAIL:?Define GIT_AUTHOR_EMAIL con tu correo real}"
: "${REPO_URL:?Define REPO_URL con la URL del repositorio privado}"
test -f pom.xml && test -d src/main/java/com/littlehorizons/erp

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git init --initial-branch=main --object-format=sha1
fi
test "$(git rev-parse --show-toplevel)" = "$(pwd -P)"
if git rev-parse --verify HEAD >/dev/null 2>&1; then
  echo 'Este script prepara la entrega inicial; no reescribe un historial existente.' >&2
  exit 1
fi
if ! git diff --cached --quiet; then
  echo 'El índice debe estar vacío antes de preparar los commits.' >&2
  exit 1
fi

# RUN_QA=0 is reserved for simulation using an identical, already tested copy.
if [ "${RUN_QA:-1}" = 1 ]; then
  ./scripts/test-integration.sh
fi

git config user.name "$GIT_AUTHOR_NAME"
git config user.email "$GIT_AUTHOR_EMAIL"
git symbolic-ref HEAD refs/heads/main
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REPO_URL"
else
  git remote add origin "$REPO_URL"
fi

erp_src=src/main/java/com/littlehorizons/erp

git add .gitignore .env.example pom.xml "$erp_src/model" "$erp_src/util" \
  "$erp_src/view" "$erp_src/service/TransactionalService.java" \
  "$erp_src/dao/jdbc/Jdbc.java" "$erp_src"/dao/*.java
git commit -m "build: configure Java 17 Maven and shared MVC infrastructure"

git add "$erp_src/dao/jdbc/JdbcStudentDao.java" \
  "$erp_src/dao/jdbc/JdbcAcademicDao.java" "$erp_src/dao/jdbc/JdbcCatalogDao.java" \
  "$erp_src/service/StudentService.java" "$erp_src/service/AcademicService.java" \
  "$erp_src/service/CatalogService.java" "$erp_src/controller/StudentController.java" \
  "$erp_src/controller/StudentAdministrationController.java" \
  "$erp_src/controller/AcademicController.java" \
  "$erp_src/controller/AcademicAdministrationController.java" \
  "$erp_src/controller/CatalogController.java"
git commit -m "feat: implement full academic CRUD and catalog administration"

git add "$erp_src/dao/jdbc/JdbcActivityDao.java" "$erp_src/service/ActivityService.java" \
  "$erp_src/controller/ActivityController.java" "$erp_src/controller/ActivityAdministrationController.java"
git commit -m "feat: implement full CRUD for extracurricular module"

git add "$erp_src/dao/jdbc/JdbcFinanceDao.java" "$erp_src/service/FinanceService.java" \
  "$erp_src/controller/FinanceController.java" "$erp_src/controller/FinanceAdministrationController.java"
git commit -m "feat: implement transactional payments and recoverable PDF invoices"

git add "$erp_src/dao/jdbc/JdbcResourceDao.java" "$erp_src/service/ResourceService.java" \
  "$erp_src/controller/ResourceController.java" "$erp_src/controller/ResourceAdministrationController.java"
git commit -m "feat: implement inventory CRUD and exclusive resource loans"

git add "$erp_src/Main.java"
git commit -m "feat: wire all modules and Spanish administration menus"

git add src/test scripts
git commit -m "test: verify CRUD concurrency rollback and historical regressions"

git add README.md docs
git commit -m "docs: finalize MySQL schema audit and delivery documentation"

test -z "$(git status --porcelain)"
test "$(git branch --show-current)" = main
final_sha=$(git rev-parse --verify 'main^{commit}')
printf 'Repositorio: %s\nRama: main\nSHA completo: %s\n' "$REPO_URL" "$final_sha"

# Create the remote as PRIVATE and authorize the instructor before publishing.
# This script never creates a GitHub repository or pushes automatically.
# git push -u origin main
