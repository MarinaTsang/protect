package com.library.dexknife.shell.res;


import com.library.dexknife.shell.res.data.ResPackage;
import com.library.dexknife.shell.res.decoder.RawARSCDecoder;
import com.library.dexknife.shell.res.util.ExtFile;
import com.library.dexknife.shell.res.util.FileOperation;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import brut.androlib.AndrolibException;
import brut.directory.DirectoryException;

/**
 * @author shwenzhang
 */
public class ApkDecoder {

    private final Configuration            config;
    private ExtFile mApkFile;
    private       File                     mOutDir;
    private       File                     mOutTempARSCFile;
    private       File                     mOutARSCFile;
    private       File                     mOutResFile;
    private       File                     mRawResFile;
    private       File                     mOutTempDir;
    private       File                     mResMappingFile;
    private       HashMap<String, Integer> mCompressData;

    private final HashSet<Path>            mRawResourceFiles = new HashSet<>();

    private void copyOtherResFiles() throws IOException {
        if (mRawResourceFiles.isEmpty())
            return;

        Path resPath = mRawResFile.toPath();
        Path destPath = mOutResFile.toPath();

        for (Path path : mRawResourceFiles) {
            Path relativePath = resPath.relativize(path);
            Path dest = destPath.resolve(relativePath);

            System.out.printf("copy res file not in resources.arsc file:%s\n", relativePath.toString());
            FileOperation.copyFileUsingStream(path.toFile(), dest.toFile());

        }
    }
    class ResourceFilesVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            mRawResourceFiles.add(file);
            return FileVisitResult.CONTINUE;
        }
    }

    public void removeCopiedResFile(Path key) {
        mRawResourceFiles.remove(key);
    }

    public ApkDecoder(Configuration config) {
        this.config = config;
    }

    public Configuration getConfig() {
        return config;
    }

    public boolean hasResources() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("resources.arsc");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public void setApkFile(File apkFile) {
        mApkFile = new ExtFile(apkFile);
    }

    private void ensureFilePath() throws IOException {
        com.library.dexknife.shell.res.util.Utils.cleanDir(mOutDir);

        String unZipDest = new File(mOutDir, com.library.dexknife.shell.res.util.TypedValue.UNZIP_FILE_PATH).getAbsolutePath();
        System.out.printf("unziping apk to %s\n", unZipDest);
        mCompressData = FileOperation.unZipAPk(mApkFile.getAbsoluteFile().getAbsolutePath(), unZipDest);
        dealWithCompressConfig();
        //将res混淆成r
        if (!config.mKeepRoot) {
            mOutResFile = new File(mOutDir.getAbsolutePath() + File.separator + com.library.dexknife.shell.res.util.TypedValue.RES_FILE_PATH);
        } else {
            mOutResFile = new File(mOutDir.getAbsolutePath() + File.separator + "res");
        }

        //这个需要混淆各个文件夹
        mRawResFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + com.library.dexknife.shell.res.util.TypedValue.UNZIP_FILE_PATH + File.separator + "res");
        mOutTempDir = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + com.library.dexknife.shell.res.util.TypedValue.UNZIP_FILE_PATH);

        //这里纪录原始res目录的文件
        Files.walkFileTree(mRawResFile.toPath(), new ResourceFilesVisitor());

        if (!mRawResFile.exists() || !mRawResFile.isDirectory()) {
            throw new IOException("can not found res dir in the apk or it is not a dir");
        }

        mOutTempARSCFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + "resources_temp.arsc");
        mOutARSCFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + "resources.arsc");

        String basename = mApkFile.getName().substring(0, mApkFile.getName().indexOf(".apk"));
        mResMappingFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator
            + com.library.dexknife.shell.res.util.TypedValue.RES_MAPPING_FILE + basename + com.library.dexknife.shell.res.util.TypedValue.TXT_FILE);
    }

    /**
     * 根据config来修改压缩的值
     */
    private void dealWithCompressConfig() {
        if (config.mUseCompress) {
            HashSet<Pattern> patterns = config.mCompressPatterns;
            if (!patterns.isEmpty()) {
                for (Entry<String, Integer> entry : mCompressData.entrySet()) {
                    String name = entry.getKey();
                    for (Iterator<Pattern> it = patterns.iterator(); it.hasNext(); ) {
                        Pattern p = it.next();
                        if (p.matcher(name).matches()) {
                            mCompressData.put(name, com.library.dexknife.shell.res.util.TypedValue.ZIP_DEFLATED);
                        }
                    }

                }
            }
        }
    }

    public HashMap<String, Integer> getCompressData() {
        return mCompressData;
    }

    public File getOutDir() {
        return mOutDir;
    }

    public void setOutDir(File outDir) throws AndrolibException {
        mOutDir = outDir;
    }

    public File getOutResFile() {
        return mOutResFile;
    }

    public File getRawResFile() {
        return mRawResFile;
    }

    public File getOutTempARSCFile() {
        return mOutTempARSCFile;
    }

    public File getOutARSCFile() {
        return mOutARSCFile;
    }

    public File getOutTempDir() {
        return mOutTempDir;
    }

    public File getResMappingFile() {
        return mResMappingFile;
    }


    public void decode() throws AndrolibException, IOException, DirectoryException {
        if (hasResources()) {
            ensureFilePath();
            // read the resources.arsc checking for STORED vs DEFLATE compression
            // this will determine whether we compress on rebuild or not.
            System.out.printf("decoding resources.arsc\n");
            RawARSCDecoder.decode(mApkFile.getDirectory().getFileInput("resources.arsc"));
            ResPackage[] pkgs = com.library.dexknife.shell.res.decoder.ARSCDecoder.decode(mApkFile.getDirectory().getFileInput("resources.arsc"), this);

            //把没有纪录在resources.arsc的资源文件也拷进dest目录
            copyOtherResFiles();

            com.library.dexknife.shell.res.decoder.ARSCDecoder.write(mApkFile.getDirectory().getFileInput("resources.arsc"), this, pkgs);
        }
    }
}
