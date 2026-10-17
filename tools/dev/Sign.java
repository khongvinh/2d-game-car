import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.*;

/** Usage: Sign <base.apk> <classes.dex> <keystore> <out.apk> */
public class Sign {
    public static void main(String[] a) throws Exception {
        File unsigned = new File(a[3] + ".unsigned");
        // Rebuild the zip: keep stored entries stored and 4-byte aligned, add classes.dex.
        try (ZipFile in = new ZipFile(a[0]);
             CountingOut cnt = new CountingOut(new FileOutputStream(unsigned));
             ZipOutputStream out = new ZipOutputStream(cnt)) {
            List<ZipEntry> entries = Collections.list((Enumeration<ZipEntry>) in.entries());
            for (ZipEntry e : entries) {
                byte[] data = in.getInputStream(e).readAllBytes();
                write(out, cnt, e.getName(), data, e.getMethod() == ZipEntry.STORED);
            }
            byte[] dex = java.nio.file.Files.readAllBytes(new File(a[1]).toPath());
            write(out, cnt, "classes.dex", dex, false);
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream f = new FileInputStream(a[2])) { ks.load(f, "android".toCharArray()); }
        PrivateKey key = (PrivateKey) ks.getKey("androiddebugkey", "android".toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate("androiddebugkey");
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("CERT", key, List.of(cert)).build();
        new ApkSigner.Builder(List.of(sc)).setInputApk(unsigned).setOutputApk(new File(a[3]))
            .setMinSdkVersion(24).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
            .build().sign();
        unsigned.delete();
        ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[3])).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme() + " v2=" + r.isVerifiedUsingV2Scheme() + " v3=" + r.isVerifiedUsingV3Scheme());
        for (ApkVerifier.IssueWithParams i : r.getErrors()) System.out.println("ERR " + i);
    }

    static void write(ZipOutputStream out, CountingOut cnt, String name, byte[] data, boolean stored) throws IOException {
        ZipEntry ne = new ZipEntry(name);
        ne.setTime(315532800000L + 86400000L);
        if (stored) {
            CRC32 crc = new CRC32(); crc.update(data);
            ne.setMethod(ZipEntry.STORED); ne.setSize(data.length); ne.setCompressedSize(data.length); ne.setCrc(crc.getValue());
            long headerEnd = cnt.count + 30 + name.getBytes("UTF-8").length;
            int n = (int) ((4 - (headerEnd + 4) % 4) % 4);
            byte[] extra = new byte[4 + n];
            extra[0] = (byte) 0x35; extra[1] = (byte) 0xD9; extra[2] = (byte) n; extra[3] = 0;
            ne.setExtra(extra);
        } else {
            ne.setMethod(ZipEntry.DEFLATED);
        }
        out.putNextEntry(ne); out.write(data); out.closeEntry();
    }

    static class CountingOut extends FilterOutputStream {
        long count;
        CountingOut(OutputStream o) { super(o); }
        @Override public void write(int b) throws IOException { out.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); count += len; }
    }
}
