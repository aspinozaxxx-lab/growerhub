import os
import shutil
from os.path import join

from SCons.Script import Import

Import("env")
pio_platform = env.PioPlatform()
toolchain_dir = pio_platform.get_package_dir("toolchain-gccmingw32")

if toolchain_dir:
    bin_dir = join(toolchain_dir, "bin")
    env.AppendENVPath("PATH", bin_dir)
    os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")

    build_dir = env.subst("$BUILD_DIR")
    os.makedirs(build_dir, exist_ok=True)
    for dll in ("libstdc++-6.dll", "libgcc_s_dw2-1.dll", "libwinpthread-1.dll"):
        src = join(bin_dir, dll)
        if os.path.exists(src):
            shutil.copy(src, build_dir)

    exe = ".exe"
    env.Replace(
        AR=join(bin_dir, "i686-w64-mingw32-ar" + exe),
        AS=join(bin_dir, "i686-w64-mingw32-as" + exe),
        CC=join(bin_dir, "i686-w64-mingw32-gcc" + exe),
        CXX=join(bin_dir, "i686-w64-mingw32-g++" + exe),
        GDB=join(bin_dir, "i686-w64-mingw32-gdb" + exe),
        LINK=join(bin_dir, "i686-w64-mingw32-g++" + exe),
        RANLIB=join(bin_dir, "i686-w64-mingw32-ranlib" + exe),
        OBJCOPY=join(bin_dir, "i686-w64-mingw32-objcopy" + exe),
        OBJDUMP=join(bin_dir, "i686-w64-mingw32-objdump" + exe),
        NM=join(bin_dir, "i686-w64-mingw32-nm" + exe),
        STRIP=join(bin_dir, "i686-w64-mingw32-strip" + exe),
    )
