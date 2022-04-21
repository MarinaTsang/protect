package com.library.dexknife.shell.apkparser;

import com.library.dexknife.shell.apkparser.bean.ApkMeta;
import com.library.dexknife.shell.apkparser.bean.ApkSignStatus;
import com.library.dexknife.shell.apkparser.bean.CertificateMeta;
import com.library.dexknife.shell.apkparser.exception.ParserException;
import com.library.dexknife.shell.apkparser.parser.ApkMetaTranslator;
import com.library.dexknife.shell.apkparser.parser.BinaryXmlParser;
import com.library.dexknife.shell.apkparser.parser.ResourceTableParser;
import com.library.dexknife.shell.apkparser.struct.AndroidConstants;
import com.library.dexknife.shell.apkparser.utils.Utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * ApkParser and result holder.
 * This class is not thread-safe.
 *
 * @author dongliu
 */
public class ApkParser implements Closeable {

    private com.library.dexknife.shell.apkparser.bean.DexClass[] dexClasses;
    private com.library.dexknife.shell.apkparser.struct.resource.ResourceTable resourceTable;

    private String manifestXml;
    private ApkMeta apkMeta;
    private Set<Locale> locales;
    private List<CertificateMeta> certificateMetaList;
    private final ZipFile zf;
    private File apkFile;

    private static final Locale DEFAULT_LOCALE = Locale.US;

    /**
     * default use empty locale
     */
    private Locale preferredLocale = DEFAULT_LOCALE;

    public ApkParser(File apkFile) throws IOException {
        this.apkFile = apkFile;
        // create zip file cost time, use one zip file for apk parser life cycle
        this.zf = new ZipFile(apkFile);
    }

    public ApkParser(String filePath) throws IOException {
        this(new File(filePath));
    }

    /**
     * return decoded AndroidManifest.xml
     *
     * @return decoded AndroidManifest.xml
     */
    public String getManifestXml() throws IOException {
        if (this.manifestXml == null) {
            parseManifestXml();
        }
        return this.manifestXml;
    }

    /**
     * return decoded AndroidManifest.xml
     *
     * @return decoded AndroidManifest.xml
     */
    public ApkMeta getApkMeta() throws IOException {
        if (this.apkMeta == null) {
            parseApkMeta();
        }
        return this.apkMeta;
    }

    /**
     * get locales supported from resource file
     *
     * @return decoded AndroidManifest.xml
     * @throws IOException
     */
    public Set<Locale> getLocales() throws IOException {
        if (this.locales == null) {
            parseResourceTable();
        }
        return this.locales;
    }

    /**
     * get the apk's certificates.
     */
    public List<CertificateMeta> getCertificateMetaList() throws IOException,
            CertificateException {
        if (this.certificateMetaList == null) {
            parseCertificate();
        }
        return this.certificateMetaList;
    }

    private void parseCertificate() throws IOException, CertificateException {
        ZipEntry entry = null;
        Enumeration<? extends ZipEntry> enu = zf.entries();
        while (enu.hasMoreElements()) {
            ZipEntry ne = enu.nextElement();
            if (ne.isDirectory()) {
                continue;
            }
            if (ne.getName().toUpperCase().endsWith(".RSA")
                    || ne.getName().toUpperCase().endsWith(".DSA")) {

                entry = ne;
                break;
            }
        }
        if (entry == null) {
            throw new ParserException("ApkParser certificate not found");
        }
        try (InputStream in = zf.getInputStream(entry)) {
            com.library.dexknife.shell.apkparser.parser.CertificateParser parser = new com.library.dexknife.shell.apkparser.parser.CertificateParser(in);
            parser.parse();
            this.certificateMetaList = parser.getCertificateMetas();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * parse manifest.xml, get apkMeta.
     *
     * @throws IOException
     */
    private void parseApkMeta() throws IOException {
        if (this.manifestXml == null) {
            parseManifestXml();
        }
    }

    /**
     * parse manifest.xml, get manifestXml as xml text.
     *
     * @throws IOException
     */
    private void parseManifestXml() throws IOException {
        com.library.dexknife.shell.apkparser.parser.XmlTranslator xmlTranslator = new com.library.dexknife.shell.apkparser.parser.XmlTranslator();
        ApkMetaTranslator translator = new ApkMetaTranslator();
        com.library.dexknife.shell.apkparser.parser.XmlStreamer xmlStreamer = new com.library.dexknife.shell.apkparser.parser.CompositeXmlStreamer(xmlTranslator, translator);
        transBinaryXml(AndroidConstants.MANIFEST_FILE, xmlStreamer);
        this.manifestXml = xmlTranslator.getXml();
        if (this.manifestXml == null) {
            throw new ParserException("manifest xml not exists");
        }
        this.apkMeta = translator.getApkMeta();
    }

    /**
     * trans binary xml file to text xml file.
     *
     * @param path the xml file path in apk file
     * @return the text. null if file not exists
     * @throws IOException
     */
    public String transBinaryXml(String path) throws IOException {
        ZipEntry entry = Utils.getEntry(zf, path);
        if (entry == null) {
            return null;
        }
        if (this.resourceTable == null) {
            parseResourceTable();
        }

        com.library.dexknife.shell.apkparser.parser.XmlTranslator xmlTranslator = new com.library.dexknife.shell.apkparser.parser.XmlTranslator();
        transBinaryXml(path, xmlTranslator);
        return xmlTranslator.getXml();
    }

    /**
     * get the apk icon file as bytes.
     *
     * @return the apk icon data,null if icon not found
     * @throws IOException
     */
    public com.library.dexknife.shell.apkparser.bean.Icon getIconFile() throws IOException {
        ApkMeta apkMeta = getApkMeta();
        String iconPath = apkMeta.getIcon();
        if (iconPath == null) {
            return null;
        }
        return new com.library.dexknife.shell.apkparser.bean.Icon(iconPath, getFileData(iconPath));
    }


    private void transBinaryXml(String path, com.library.dexknife.shell.apkparser.parser.XmlStreamer xmlStreamer) throws IOException {
        ZipEntry entry = Utils.getEntry(zf, path);
        if (entry == null) {
            return;
        }
        if (this.resourceTable == null) {
            parseResourceTable();
        }

        InputStream in = zf.getInputStream(entry);
        ByteBuffer buffer = ByteBuffer.wrap(Utils.toByteArray(in));
        BinaryXmlParser binaryXmlParser = new BinaryXmlParser(buffer, resourceTable);
        binaryXmlParser.setLocale(preferredLocale);
        binaryXmlParser.setXmlStreamer(xmlStreamer);
        binaryXmlParser.parse();
    }


    /**
     * get class infos form dex file. currently only class name
     */
    public com.library.dexknife.shell.apkparser.bean.DexClass[] getDexClasses() throws IOException {
        if (this.dexClasses == null) {
            parseDexFile();
        }
        return this.dexClasses;
    }

    private void parseDexFile() throws IOException {
        ZipEntry resourceEntry = Utils.getEntry(zf, AndroidConstants.DEX_FILE);
        if (resourceEntry == null) {
            throw new ParserException("Resource table not found");
        }
        InputStream in = zf.getInputStream(resourceEntry);
        ByteBuffer buffer = ByteBuffer.wrap(Utils.toByteArray(in));
        com.library.dexknife.shell.apkparser.parser.DexParser dexParser = new com.library.dexknife.shell.apkparser.parser.DexParser(buffer);
        dexParser.parse();
        this.dexClasses = dexParser.getDexClasses();
    }

    /**
     * read file in apk into bytes
     */
    public byte[] getFileData(String path) throws IOException {
        ZipEntry entry = Utils.getEntry(zf, path);
        if (entry == null) {
            return null;
        }

        InputStream inputStream = zf.getInputStream(entry);
        return Utils.toByteArray(inputStream);
    }

    /**
     * check apk sign
     *
     * @throws IOException
     */
    public ApkSignStatus verifyApk() throws IOException {
        ZipEntry entry = Utils.getEntry(zf, "META-INF/MANIFEST.MF");
        if (entry == null) {
            // apk is not signed;
            return ApkSignStatus.notSigned;
        }

        JarFile jarFile = new JarFile(this.apkFile);
        Enumeration<JarEntry> entries = jarFile.entries();
        byte[] buffer = new byte[8192];

        while (entries.hasMoreElements()) {
            JarEntry e = entries.nextElement();
            if (e.isDirectory()) {
                continue;
            }
            try (InputStream in = jarFile.getInputStream(e)) {
                // Read in each jar entry. A security exception will be thrown if a signature/digest check fails.
                int count;
                while ((count = in.read(buffer, 0, buffer.length)) != -1) {
                    // Don't care
                }
            } catch (SecurityException se) {
                return ApkSignStatus.incorrect;
            }
        }
        return ApkSignStatus.signed;
    }

    /**
     * parse resource table.
     */
    private void parseResourceTable() throws IOException {
        ZipEntry entry = Utils.getEntry(zf, AndroidConstants.RESOURCE_FILE);
        if (entry == null) {
            // if no resource entry has been found, we assume it is not needed by this APK
            this.resourceTable = new com.library.dexknife.shell.apkparser.struct.resource.ResourceTable();
            this.locales = Collections.emptySet();
            return;
        }

        this.resourceTable = new com.library.dexknife.shell.apkparser.struct.resource.ResourceTable();
        this.locales = Collections.emptySet();

        InputStream in = zf.getInputStream(entry);
        ByteBuffer buffer = ByteBuffer.wrap(Utils.toByteArray(in));
        ResourceTableParser resourceTableParser = new ResourceTableParser(buffer);
        resourceTableParser.parse();
        this.resourceTable = resourceTableParser.getResourceTable();
        this.locales = resourceTableParser.getLocales();
    }

    @Override
    public void close() throws IOException {
        this.certificateMetaList = null;
        this.resourceTable = null;
        this.certificateMetaList = null;
        zf.close();
    }

    public Locale getPreferredLocale() {
        return preferredLocale;
    }

    /**
     * The locale preferred. Will cause getManifestXml / getApkMeta to return different values.
     * The default value is from os default locale setting.
     */
    public void setPreferredLocale(Locale preferredLocale) {
        if (!Objects.equals(this.preferredLocale, preferredLocale)) {
            this.preferredLocale = preferredLocale;
            this.manifestXml = null;
            this.apkMeta = null;
        }
    }
}
