package jungle.battery.fast.utils;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.Map;

import dalvik.system.DexClassLoader;

import static java.lang.System.out;

/**
 * Dex文件保护工具类
 *
 * @author linchaolong
 */
public class DexDecrypt {

    private static final int BUFF_SIZE = 1024 * 1024 * 5; //5MB
    private static final String TAG = "ApkProtect";

    private Context context = null;
    private Class<?> loadedApkClass = null;
    private WeakReference<?> loadedApkRef = null;

    public DexDecrypt(Context ctx) {
        this.context = ctx;
        try {
            // 初始化
            // 1. 得到当前的ActivityThread
            // 加载ActivityThread的字节码
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            // 利用反射得到currentActivityThread方法
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            // 调用currentActivityThread方法得到当前ActivityThread对象
            Object activityThread = currentActivityThreadMethod.invoke(null);

            // 2. 得到LoadedApk的弱引用
            //final ArrayMap<String, WeakReference<LoadedApk>> mPackages = new ArrayMap<String, WeakReference<LoadedApk>>();
            // 得到LoadedApk对象
            Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
            mPackagesField.setAccessible(true); //取消默认 Java 语言访问控制检查的能力（暴力反射）
            Map mPackages = (Map) mPackagesField.get(activityThread);
            // 得到LoadedApk对象的弱引用
            loadedApkRef = (WeakReference) mPackages.get(ctx.getPackageName());

            // 3. 修改LoadedApk对象中mClassLoader字段
            loadedApkClass = Class.forName("android.app.LoadedApk");
//			Field mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader");
//			mClassLoaderField.setAccessible(true);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 得到系统ClassLoader
     *
     * @return
     */
    public ClassLoader getAppClassLoader() {
        try {
            Field mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader");
            mClassLoaderField.setAccessible(true);
            return (ClassLoader) mClassLoaderField.get(loadedApkRef.get());// 得到系统ClassLoader
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 修改当前应用ClassLoader
     *
     * @param newClassLoader
     */
    private boolean setAppClassLoader(ClassLoader newClassLoader) {
        try {
            // 3. 修改LoadedApk对象中mClassLoader字段
            Field mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader");
            mClassLoaderField.setAccessible(true);
            mClassLoaderField.set(loadedApkRef.get(), newClassLoader); // 修改当前ClassLoader为自定义ClassLoader
            return true;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 加载加密后的dex文件
     *
     * @param dexName 加密的dex文件的输入流
     * @return ClassLoader
     */
    public ClassLoader loadEncryptDex(String[] dexName) {

        try {
            //通过反射修改ActivityThread中LoadedApk的ClassLoader字段
            ClassLoader appClassLoader =  ClassLoader.getSystemClassLoader();
            String pahts="";
            for (int i = 0; i < dexName.length; i++) {
                String dexExtName=dexName[i].replace("jar","dex");
                pahts+=createEncryptDexLoader(dexName[i],context.getAssets().open("libprotected.jar/" + dexName[i]), dexExtName, appClassLoader)+":"; // 优先系统的
            }
            // 经过优化的dex输出目录
            File odexDir = context.getDir("apktoolplus_odex", Context.MODE_PRIVATE);
            // libs目录
            String libPath = context.getApplicationInfo().nativeLibraryDir;
            // 创建类加载器，加载解密后的dex文件
            ClassLoader dexClassLoader;
            if (appClassLoader != null) {
                dexClassLoader = new DexClassLoader(pahts, odexDir.getAbsolutePath(), libPath, appClassLoader);
            } else {
                dexClassLoader = new DexClassLoader(pahts, odexDir.getAbsolutePath(), libPath, context.getClassLoader());
            }
            setAppClassLoader(dexClassLoader);// 修改当前ClassLoader为自定义ClassLoader
            // 删除解密后的dex文件
            deleteDir(context.getDir("apktoolplus_dex", Context.MODE_PRIVATE));
            return dexClassLoader;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 在内存在加载加密的dex文件
     *
     * @param in 加密的dex文件的输入流
     * @return ClassLoader
     */
    public ClassLoader loadEncryptDexMemory(FileInputStream in) {
        try {
            // 读取dex文件数据
            ByteArrayOutputStream outBuff = new ByteArrayOutputStream();
            byte[] temp = new byte[BUFF_SIZE];
            int len = -1;
            while ((len = in.read(temp)) != -1) {
                outBuff.write(temp, 0, len);
            }
            return loadEncryptDexMemory(outBuff.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IO.close(in);
        }
        return null;
    }

    /**
     * 在内存在加载加密的dex文件
     *
     * @param dexBytes 加密的dex文件的字节数据
     * @return ClassLoader
     */
    public ClassLoader loadEncryptDexMemory(byte[] dexBytes) {
        try {
            ClassLoader appClassLoader = getAppClassLoader();

            ClassDexLoder newClassLoader = new ClassDexLoder(
                    context,
                    dexBytes,
                    null,
                    appClassLoader,
                    context.getPackageResourcePath(),
                    context.getDir(".dex", Context.MODE_PRIVATE).getAbsolutePath());

            setAppClassLoader(newClassLoader);// 修改当前ClassLoader为自定义ClassLoader

            return newClassLoader;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 创建一个加密dex文件的DexLoader
     *
     * @param in          加密的dex文件的输入流
     * @param outFileName 解密文件输入名称
     * @param parent      父类加载器
     * @return ClassLoader
     */
    public String createEncryptDexLoader(String filename,InputStream in, String outFileName, ClassLoader parent) {

        // 解密dex文件的File对象
        File decryptFile = new File(context.getDir("apktoolplus_dex", Context.MODE_PRIVATE), outFileName);
        // 解密dex文件
        DexDecrypt.decryptDex(context,filename,in, decryptFile);

        if (!decryptFile.exists()) {
            Log.e(TAG, "dex decrypt failure!!!");
        }
        // 使用优化过的odex文件替换解密的dex
//        File odexFile = new File(odexDir, outFileName);
//        repleceFile(odexFile, decryptFile);

        return decryptFile.getPath();
    }

    private void deleteDir(File dir) {
        for (File file : dir.listFiles()) {
            file.delete();
        }
        dir.delete();
    }

    private void repleceFile(File src, File dst) {
        if (src.exists()) {
            try {
                copyFile(src, dst);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        if (dst.exists()) {
            dst.delete();
        }
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    /**
     * 解密Dex文件
     *
     * @param in      加密dex文件输入流
     * @param outFile 输出解密文件
     * @return 是否解密成功
     */
    public static boolean decryptDex(Context context,String filename,InputStream in, File outFile) {
        // 加密dex文件所在目录
        File outDir = outFile.getParentFile();

        // 如果目录不存在则创建
        if (!outDir.exists() && outDir.isDirectory()) {
            outDir.mkdirs();
        }

        try {
            if (outFile.exists()) {
                outFile.delete();
            }

            AESHelper.decryptDex(context,filename,in,outFile);



            //替换方法ToolPlus.decryptDex
//            FileOutputStream out = null;
//            out = new FileOutputStream(outFile);
//            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
//            // 读取加密数据
//            byte[] buff = new byte[BUFF_SIZE];
//            int len;
//            while ((len = in.read(buff)) != -1) {
//                byteOutput.write(buff, 0, len);
//            }
//            // 调用native方法解密dex文件数据
//            byte[] dexBytes = byteOutput.toByteArray();
//            byte[] decryptBuff = AESHelper.decrypt(dexBytes);
//            out.write(decryptBuff, 0, decryptBuff.length);
//            out.flush();


            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            // 释放资源
            IO.close(in);
            IO.close(out);
        }
        return false;
    }

//    /**
//     * 解密Dex文件
//     *
//     * @param encryptFile 加密文件
//     * @param outFile     输出解密文件
//     * @return 是否解密成功
//     */
//    public static boolean decryptDex(File encryptFile, File outFile) {
//
//        if (!encryptFile.exists()) {
//            Log.e(TAG, "加密文件 '" + encryptFile.getPath() + " ''不存在");
//            return false;
//        }
//
//        try {
//            return decryptDex(encryptFile.getName(),new FileInputStream(encryptFile), outFile);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        return false;
//    }

}
