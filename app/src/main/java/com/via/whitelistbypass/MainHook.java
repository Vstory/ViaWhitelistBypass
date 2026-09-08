package com.via.whitelistbypass;

import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterfaceWrapper;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
















public class MainHook extends XposedModule {

    private static final String TAG = "ViaTune";


    private ClassLoader mAppClassLoader;




    public static class FalseHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) {
            return Boolean.FALSE;
        }
    }


    public static class VoidHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) {
            return null;
        }
    }



    public MainHook() {
        super();
    }







    private int hookMethod(ClassLoader cl, String className, String methodName, XposedInterface.Hooker hooker) {
        int count = 0;
        try {

            Class<?> clazz = Class.forName(className, false, cl);
            Method[] methods = clazz.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getName().equals(methodName)) {
                    hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                           .intercept(hooker);
                    count++;
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }





    private int installHooks(ClassLoader cl) {
        FalseHooker falseHooker = new FalseHooker();
        VoidHooker voidHooker = new VoidHooker();
        int total = 0;

        String targetClass = "r9.k";


        total += hookMethod(cl, targetClass, "s", falseHooker);

        total += hookMethod(cl, targetClass, "a", falseHooker);

        total += hookMethod(cl, targetClass, "c", falseHooker);

        total += hookMethod(cl, targetClass, "e", falseHooker);

        total += hookMethod(cl, targetClass, "n", falseHooker);

        total += hookMethod(cl, targetClass, "u", voidHooker);

        return total;
    }


    private int installUiEnhance(ClassLoader cl) {
        return new UiSearchEnhance(this, cl).install();
    }



    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "api102 module loaded");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        ClassLoader cl = param.getClassLoader();
        mAppClassLoader = cl;

        int count = installHooks(cl);
        int ui = installUiEnhance(cl);
        if (count == 0 && ui == 0) {
            log(Log.INFO, TAG, "ViaTune: subprocess active (no whitelist class r9.k / no settings pages) - skipped");
            return;
        }
        log(Log.INFO, TAG, "ViaTune: hooks installed"
                + " | r9.k whitelist bypass: " + count + "/6 methods"
                + " | ui search enhance: " + ui + " hooks");
    }

    @Override
    public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        return true;
    }

    @Override
    public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
        ClassLoader cl = null;


        List<XposedInterface.HookHandle> oldHandles = param.getOldHookHandles();
        if (!oldHandles.isEmpty()) {
            XposedInterface.HookHandle handle = oldHandles.get(0);
            Executable executable = handle.getExecutable();
            cl = executable.getDeclaringClass().getClassLoader();
        }


        for (XposedInterface.HookHandle handle : oldHandles) {
            handle.unhook();
        }


        if (cl == null) {
            cl = mAppClassLoader;
        }
        if (cl == null) {
            return;
        }


        installHooks(cl);
        installUiEnhance(cl);
        log(Log.INFO, TAG, "hot reloaded, hooks reinstalled");
    }
}
