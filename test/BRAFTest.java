package test;

import org.kihara.util.SeekableFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

public class BRAFTest {

    public static final String TEST_STRING =
	">of|PDBID|CHAIN|SEQUENCE\n" +
	"VLSPADKTNVKAAWGKVGAHAGEYGAEALERMFLSFPTTKTYFPHFDLSHGSAQVKGHGKKVADALTNAVAHVDDMPNAL\n" +
	"SALSDLHAHKLRVDPVNFKLLSHCLLVTLAAHLPAEFTPAVHASLDKFLASVSTVLTSKYR\n" +
	">1HHO:B|PDBID|CHAIN|SEQUENCE\n" +
	"VHLTPEEKSAVTALWGKVNVDEVGGEALGRLLVVYPWTQRFFESFGDLSTPDAVMGNPKVKAHGKKVLGAFSDGLAHLDN\n" +
	"LKGTFATLSELHCDKLHVDPENFRLLGNVLVCVLAHHFGKEFTPPVQAAYQKVVAGVANALAHKYH\n" +
	">of|PDBID|CHAIN|SEQUENCE\n" +
	"VLSPADKTNVKAAWGKVGAHAGEYGAEALERMFLSFPTTKTYFPHFDLSHGSAQVKGHGKKVADALTNAVAHVDDMPNAL\n" +
	"SALSDLHAHKLRVDPVNFKLLSHCLLVTLAAHLPAEFTPAVHASLDKFLASVSTVLTSKYR\n" +
	">1HHO:B|PDBID|CHAIN|SEQUENCE\n" +
	"VHLTPEEKSAVTALWGKVNVDEVGGEALGRLLVVYPWTQRFFESFGDLSTPDAVMGNPKVKAHGKKVLGAFSDGLAHLDN\n" +
	"LKGTFATLSELHCDKLHVDPENFRLLGNVLVCVLAHHFGKEFTPPVQAAYQKVVAGVANALAHKYH\n" +
	">of|PDBID|CHAIN|SEQUENCE\n" +
	"VLSPADKTNVKAAWGKVGAHAGEYGAEALERMFLSFPTTKTYFPHFDLSHGSAQVKGHGKKVADALTNAVAHVDDMPNAL\n" +
	"SALSDLHAHKLRVDPVNFKLLSHCLLVTLAAHLPAEFTPAVHASLDKFLASVSTVLTSKYR\n" +
	">1HHO:B|PDBID|CHAIN|SEQUENCE\n" +
	"VHLTPEEKSAVTALWGKVNVDEVGGEALGRLLVVYPWTQRFFESFGDLSTPDAVMGNPKVKAHGKKVLGAFSDGLAHLDN\n" +
	"LKGTFATLSELHCDKLHVDPENFRLLGNVLVCVLAHHFGKEFTPPVQAAYQKVVAGVANALAHKYH\n" +
	">of|PDBID|CHAIN|SEQUENCE\n" +
	"VLSPADKTNVKAAWGKVGAHAGEYGAEALERMFLSFPTTKTYFPHFDLSHGSAQVKGHGKKVADALTNAVAHVDDMPNAL\n" +
	"SALSDLHAHKLRVDPVNFKLLSHCLLVTLAAHLPAEFTPAVHASLDKFLASVSTVLTSKYR\n" +
	">1HHO:B|PDBID|CHAIN|SEQUENCE\n" +
	"VHLTPEEKSAVTALWGKVNVDEVGGEALGRLLVVYPWTQRFFESFGDLSTPDAVMGNPKVKAHGKKVLGAFSDGLAHLDN\n" +
	"LKGTFATLSELHCDKLHVDPENFRLLGNVLVCVLAHHFGKEFTPPVQAAYQKVVAGVANALAHKYH\n";

    /**
     *
     * @param path
     * @param item
     * @param occurrence
     * @return
     * @throws Exception
     */
    /*public static int ordinalIndexOf(Path path, char item, int occurrence) throws Exception {
        if(occurrence < 0 || path == null)
            return -1;

        int curr = 0;
        try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
            ByteBuffer buff = ByteBuffer.allocate(1 << 2); // size = 32, direct?
            buff.clear();

            for(int found = 0; sbc.read(buff) > 0; curr += buff.capacity()) {
                buff.flip();
                CharBuffer out = Charset.defaultCharset().decode(buff);

                char[] outa = out.array();
                for(int i = 0; i < out.length(); i++) {
                    if((outa[i] == item) && (++found == occurrence + 1))
                        return (curr + i);
                }
                buff.clear();
            }
        }
        return curr;
    }*/

    /**
     *
     * @param path
     * @param start
     * @param end
     * @return
     * @throws Exception
     */
    /*public static String substring(Path path, int start, int end) throws Exception {
        if(end < start || start < 0 || end < 0 || path == null)
            return null;

        try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
            ByteBuffer buff = ByteBuffer.allocate((end - start));
            sbc.position(start);
            sbc.read(buff);
            buff.flip();
            return Charset.defaultCharset().decode(buff).toString();
        }
    }*/

    /**
     *
     * @param path
     * @param item
     * @param occurrence
     * @return
     * @throws Exception
     */
    /*public static String findString(Path path, char item, int occurrence) throws Exception {
        if(occurrence < 0 || path == null)
            return null;

        int start = ordinalIndexOf(path, '>', occurrence);
        int end = ordinalIndexOf(path, '>', occurrence + 1);
        if (start == end)
            return null;
        return substring(path, start, end);
    }*/

    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("test-", "-test");
        Files.write(path, TEST_STRING.getBytes(), TRUNCATE_EXISTING);

        String out = null;
		SeekableFile abc = new SeekableFile(path);
        for(int i = 0; (out = abc.find('>', i)) != null; i++)
            System.out.print(out + "\n\n");

        /*int _start = _ordinalIndexOf(TEST_STRING, '>', 7);
        int _end = _ordinalIndexOf(TEST_STRING, '>', 8);
        if(_start > 0 && _end < 0) // fix for -1
            _end = TEST_STRING.length();
        System.out.println("A range = (" + _start + ", " + _end + ")");
        System.out.println("A string = " + TEST_STRING.substring(_start, _end));


        int start = ordinalIndexOf(path, '>', 7);
        int end = ordinalIndexOf(path, '>', 8);
        System.out.println("B range = (" + start + ", " + end + ")");
        System.out.println("B string = " + substring(path, start, end));
        //*/

        /*int a, i = 0;
        do {
            a = ordinalIndexOf(path, '>', i++);
            System.out.println("TEST pos = " + a);
        } while(a != -1);

        i = 0;
        do {
            a = _ordinalIndexOf(TEST_STRING, '>', i++);
            System.out.println("ACT pos = " + a);
        } while(a != -1);
        //*/

        /*File file = path.toFile();
        System.out.println("total read = " + test(path, '>', 1));
        System.out.println("total write = " + TEST_STRING.length());

        System.out.println("String Ordinal 1 = " + _ordinalIndexOf(TEST_STRING, '>', 0));
        System.out.println("String Ordinal 2 = " + _ordinalIndexOf(TEST_STRING, '>', 1));
        System.out.println("String Ordinal 3 = " + _ordinalIndexOf(TEST_STRING, '>', 2));

        System.out.println("String Ordinal 1 = " + ordinalIndexOf(file, '>', 0));
        System.out.println("String Ordinal 2 = " + ordinalIndexOf(file, '>', 1));
        System.out.println("String Ordinal 3 = " + ordinalIndexOf(file, '>', 2));//*/
    }
}
