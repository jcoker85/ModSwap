import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

/*
  Thank you ChatGPT for writing the majority of this class
 */
public class MD5Hash implements Runnable {

    private final File file;
    private final Map<String, String> fileHashes;
    private final ExecutorService es;
    private final Phaser ph;

    public MD5Hash(Phaser ph, ExecutorService es, File file, Map<String, String> fileHashes) {
        this.file = file;
        this.fileHashes = fileHashes;
        this.es = es;
        this.ph = ph;
    }

    public void run() {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                ph.register();
                es.execute(new MD5Hash(ph, es, f, fileHashes));
            }
        } else {
            try {
                FileInputStream fis = new FileInputStream(file);
                MessageDigest md = MessageDigest.getInstance("MD5");

                byte[] dataBytes = new byte[1024];
                int nread;
                while ((nread = fis.read(dataBytes)) != -1) {
                    md.update(dataBytes, 0, nread);
                }
                byte[] mdbytes = md.digest();

                StringBuilder sb = new StringBuilder();
                for (byte mdbyte : mdbytes) {
                    sb.append(Integer.toString((mdbyte & 0xff) + 0x100, 16).substring(1));
                }
                String md5 = sb.toString();
                fileHashes.put(file.getAbsolutePath(), md5);
                fis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ph.arrive();
    }
}