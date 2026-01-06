import os
import subprocess

from SCons.Script import Import

Import("env")

# Konstanta s bezopasnym fallback znacheniem versii, esli git nedostupen.
FALLBACK_VERSION = "grovika-unknown-0"

# Konstanta s argumentami git dlya polucheniya daty HEAD v formate YYYY-MM-DD.
GIT_HEAD_DATE_ARGS = ["show", "-s", "--format=%cs", "HEAD"]

# Funkciya ishchet kornevuyu papku git, podnimayas ot start_dir vverh po derevu.
def _find_git_root(start_dir):
    current = os.path.abspath(start_dir)
    while True:
        marker = os.path.join(current, ".git")
        if os.path.isdir(marker) or os.path.isfile(marker):
            return current
        parent = os.path.dirname(current)
        if parent == current:
            return None
        current = parent

# Funkciya zapuskaet git i vozvraschaet stdout bez perevodov stroki.
# Ispolzuetsya tolko dlya chteniya dannyh, oshibki ne dolzhny valit sborku.
def _run_git(args, git_root):
    cmd = ["git", "-C", git_root] + args
    print(f"git_cmd={' '.join(cmd)}")
    result = subprocess.run(
        cmd,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    output = result.stdout.strip()
    if output:
        print(f"git_out={output}")
    return output

# Funkciya vozvraschaet stroku versii git, esli komanda dostupna dlya etogo repo.
def _get_git_version(git_root):
    try:
        result = subprocess.run(
            ["git", "-C", git_root, "--version"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        output = result.stdout.strip()
        if output:
            print(f"git_version={output}")
        return output
    except Exception as exc:
        print(f"git_version_error={exc}")
        return None

# Funkciya poluchaet datu HEAD kommita v formate YYYY-MM-DD cherez git.
def _get_head_date(git_root):
    return _run_git(GIT_HEAD_DATE_ARGS, git_root)

# Funkciya schitaet kolichestvo kommitov za ukazannuyu datu, vklyuchaya HEAD.
def _get_daily_commit_count(git_root, date_str):
    args = [
        "rev-list",
        "--count",
        f"--since={date_str} 00:00:00",
        f"--until={date_str} 23:59:59",
        "HEAD",
    ]
    return _run_git(args, git_root)

# Funkciya sobiraet stroku versii v formate grovika-<date>-<N> ili fallback.
def _build_version(project_dir):
    git_root = _find_git_root(project_dir)
    print(f"git_root={git_root}")
    if not git_root:
        return FALLBACK_VERSION
    if not _get_git_version(git_root):
        return FALLBACK_VERSION
    try:
        date_str = _get_head_date(git_root)
        if not date_str:
            return FALLBACK_VERSION
        count_str = _get_daily_commit_count(git_root, date_str)
        if not count_str or not count_str.isdigit():
            return FALLBACK_VERSION
        return f"grovika-{date_str}-{int(count_str)}"
    except Exception as exc:
        print(f"git_error={exc}")
        return FALLBACK_VERSION

# Funkciya dobavlya define GH_FW_VER kak stroku v nastroiki kompilacii.
def _apply_define(version_str):
    env.Append(
        CPPDEFINES=[
            ("GH_FW_VER", f'\\"{version_str}\\"'),
        ]
    )
    # Diagnostika: pechataem itogovuyu stroku i spisok defines posle Append.
    cppdefines = env.get("CPPDEFINES")
    gh_fw_ver = None
    if isinstance(cppdefines, (list, tuple)):
        for item in cppdefines:
            if isinstance(item, (list, tuple)) and len(item) >= 2:
                if item[0] == "GH_FW_VER":
                    gh_fw_ver = item[1]
    print(f"computed_version={version_str}")
    print(f"computed_define=GH_FW_VER=\\\"{version_str}\\\"")
    print(f"define_value_GH_FW_VER={gh_fw_ver}")
    print(f"env_cppdefines={env.get('CPPDEFINES')}")

project_dir = env.subst("$PROJECT_DIR")
version = _build_version(project_dir)
_apply_define(version)



