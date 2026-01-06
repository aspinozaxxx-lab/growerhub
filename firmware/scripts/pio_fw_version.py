import os
import subprocess

from SCons.Script import Import

Import("env")

# Konstanta s bezopasnym fallback znacheniem versii, esli git nedostupen.
FALLBACK_VERSION = "grovika-unknown-0"

# Konstanta s argumentami git dlya polucheniya daty HEAD v formate YYYY-MM-DD.
GIT_HEAD_DATE_ARGS = ["show", "-s", "--format=%cs", "HEAD"]

# Funkciya vypolnyaet git-komandu i vozvraschaet stdout bez perevodov stroki.
# Ispolzuetsya tolko dlya chteniya dannyh, oshibki probrosyat isklyuchenie.
def _run_git(args, cwd):
    result = subprocess.run(
        ["git"] + args,
        cwd=cwd,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout.strip()

# Funkciya poluchaet datu HEAD kommita v formate YYYY-MM-DD cherez git.
def _get_head_date(project_dir):
    return _run_git(GIT_HEAD_DATE_ARGS, project_dir)

# Funkciya schitaet kolichestvo kommitov za ukazannuyu datu, vklyuchaya HEAD.
def _get_daily_commit_count(project_dir, date_str):
    args = [
        "rev-list",
        "--count",
        f"--since={date_str} 00:00:00",
        f"--until={date_str} 23:59:59",
        "HEAD",
    ]
    return _run_git(args, project_dir)

# Funkciya sobiraet stroku versii v formate grovika-<date>-<N> ili fallback.
def _build_version(project_dir):
    git_dir = os.path.join(project_dir, ".git")
    if not os.path.isdir(git_dir):
        return FALLBACK_VERSION

    try:
        date_str = _get_head_date(project_dir)
        if not date_str:
            return FALLBACK_VERSION
        count_str = _get_daily_commit_count(project_dir, date_str)
        if not count_str or not count_str.isdigit():
            return FALLBACK_VERSION
        return f"grovika-{date_str}-{int(count_str)}"
    except Exception:
        return FALLBACK_VERSION

# Funkciya dobavlya define GH_FW_VER kak stroku v nastroiki kompilacii.
def _apply_define(version_str):
    env.Append(CPPDEFINES=[("GH_FW_VER", f'"{version_str}"')])

project_dir = env.subst("$PROJECT_DIR")
version = _build_version(project_dir)
_apply_define(version)

