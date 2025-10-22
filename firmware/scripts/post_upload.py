# firmware/scripts/post_upload.py
Import("env")

import os
import shutil

def ensure_config_in_data():
    project_dir = env.subst("$PROJECT_DIR")
    data_dir = env.subst("$PROJECTDATA_DIR") or os.path.join(project_dir, "data")
    src_cfg = os.path.join(project_dir, "config.ini")
    dst_cfg = os.path.join(data_dir, "config.ini")

    try:
        os.makedirs(data_dir, exist_ok=True)
        if os.path.isfile(src_cfg):
            # Копируем config.ini из корня проекта в data перед uploadfs
            shutil.copy2(src_cfg, dst_cfg)
            print(f"[post_upload] config.ini скопирован в data: {dst_cfg}")
        else:
            # Если нет файла в корне, оставляем то, что уже в data
            print("[post_upload] Внимание: firmware/config.ini не найден — используем data/ содержимое")
    except Exception as e:
        print(f"[post_upload] Ошибка при подготовке config.ini: {e}")

def upload_fs_after_firmware(source, target, env_):
    ensure_config_in_data()
    pioexe = env_.subst("$PIOEXE") or "pio"
    env_name = env_.get("PIOENV")
    cmd = f'"{pioexe}" run -t uploadfs -e {env_name}'
    print("[post_upload] Загрузка SPIFFS (uploadfs)...")
    # Выполняем загрузку FS
    env_.Execute(cmd)

# После загрузки прошивки — заливаем SPIFFS с config.ini
env.AddPostAction("$UPLOADCMD", upload_fs_after_firmware)

