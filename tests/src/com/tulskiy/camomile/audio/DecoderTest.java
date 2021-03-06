package com.tulskiy.camomile.audio;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import android.util.Log;
import com.tulskiy.camomile.audio.formats.aac.MP4Decoder;
import com.tulskiy.camomile.audio.formats.flac.FLACDecoder;
import com.tulskiy.camomile.audio.formats.mp3.MP3Decoder;
import com.tulskiy.camomile.audio.formats.ogg.VorbisDecoder;
import com.tulskiy.camomile.audio.formats.wavpack.WavPackDecoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;


/**
 * Author: Denis_Tulskiy
 * Date: 1/20/12
 */
public class DecoderTest extends InstrumentationTestCase {
    public void testMP3() throws IOException {
        new Tester(new MP3Decoder(), "testfiles/mp3/sample.mp3", 29400).start();
    }

    public void testVorbis() throws IOException {
        new Tester(new VorbisDecoder(), "testfiles/ogg/sample.ogg", 29400).start();
    }

    public void testWavPack() throws IOException {
        new Tester(new WavPackDecoder(), "testfiles/wavpack/sample.wv", 29400).start();
    }

    public void testFLAC() throws IOException {
        new Tester(new FLACDecoder(), "testfiles/flac/sample.flac", 29400).start();
    }

    public void testMP4() throws IOException {
        new Tester(new MP4Decoder(), "testfiles/aac/sample_nero.mp4", 29400).start();
        new Tester(new MP4Decoder(), "testfiles/aac/sample_itunes_new.m4a", 29400).start();
    }

    class Tester {
        private Decoder decoder;
        private String fileName;
        private int totalSamples;
        private File input;
        private ByteBuffer reference;
        private AudioFormat fmt;

        public Tester(Decoder decoder, String fileName, int totalSamples) {
            this.decoder = decoder;
            this.fileName = fileName;
            this.totalSamples = totalSamples;
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        public void start() {
            Log.d("camomile", "decoding " + fileName);
            input = copyInputData(decoder, fileName);
            assertNotNull("Could not copy input to temp file", input);
            assertTrue("Decoder returned an error", decoder.open(input));
            fmt = decoder.getAudioFormat();

            reference = decode(-1);

            int[] testOffsets = new int[10];
            testOffsets[0] = 0;
            testOffsets[1] = 1;
            testOffsets[2] = totalSamples - 1;
            testOffsets[3] = totalSamples;

            for (int i = 4; i < testOffsets.length; i++) {
                testOffsets[i] = (int) (Math.random() * totalSamples);
            }

            for (int offset : testOffsets) {
                ByteBuffer buffer = decode(offset);

                reference.rewind();
                reference.position(offset * fmt.getFrameSize());

                while (reference.hasRemaining()) {
                    if (reference.get() != buffer.get()) {
                        Log.e("camomile", "error, not identical result at position: " + buffer.position());

                        try {
                            FileOutputStream fos1 = new FileOutputStream("/sdcard/frame1.dat");
                            fos1.getChannel().write(reference);
                            fos1.close();
                            FileOutputStream fos2 = new FileOutputStream("/sdcard/frame2.dat");
                            fos2.getChannel().write(buffer);
                            fos2.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        decoder.close();
                        input.delete();
                        fail();
                    }
                }
            }
            decoder.close();
            input.delete();
        }

        private ByteBuffer decode(int offset) {
            Log.d("camomile", "seek to " + offset);
            if (offset >= 0)
                decoder.seek(offset);
            else
                offset = 0;

            ByteBuffer output = ByteBuffer.allocate(fmt.getFrameSize() * totalSamples + 10000);

            byte[] buf = new byte[65536];

            int samplesDecoded = 0;
            while (true) {
                int len = decoder.decode(buf);
                if (len == -1) {
                    break;
                }
                samplesDecoded += len;
                output.put(buf, 0, len);
            }
            Log.d("camomile", "decoded " + samplesDecoded / fmt.getFrameSize() + " samples");
            assertEquals((totalSamples - offset) * fmt.getFrameSize(), output.position());
            return (ByteBuffer) output.rewind();
        }

        private File copyInputData(Decoder decoder, String fileName) {
            try {
                AssetManager am = getInstrumentation().getContext().getAssets();
                InputStream is = am.open(fileName);

                File file = File.createTempFile(decoder.getClass().getCanonicalName(), "input.dat");
                file.deleteOnExit();

                FileOutputStream fos = new FileOutputStream(file);

                byte[] buf = new byte[1024];
                while (is.available() > 0) {
                    int len = is.read(buf);
                    fos.write(buf, 0, len);
                }

                is.close();
                fos.close();
                return file;
            } catch (IOException e) {
                fail(e.getMessage());
            }
            return null;
        }
    }
}
