package com.iyxan23.sketch.collab;

import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.sketchware.*;
import io.sketchware.project.Project;
import io.sketchware.project.SketchwareProject;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;

public class Util {

    static boolean decryptError = false;

    public static String base64encode(String txt) {
        byte[] data = txt.getBytes();
        return Base64.encodeToString(data, Base64.DEFAULT);
    }

    public static String base64decode(String base64) {
        byte[] data = Base64.decode(base64, Base64.DEFAULT);
        return new String(data);
    }

    public static String decrypt(String path) {
        try {
            Cipher instance = Cipher.getInstance("AES/CBC/PKCS5Padding");;
            byte[] bytes = "sketchwaresecure".getBytes();
            instance.init(2, new SecretKeySpec(bytes, "AES"), new IvParameterSpec(bytes));
            RandomAccessFile randomAccessFile = new RandomAccessFile(path, "r");
            byte[] bArr = new byte[((int) randomAccessFile.length())];
            randomAccessFile.readFully(bArr);

            decryptError = false;
            return new String(instance.doFinal(bArr));
        } catch (Exception e) {
            Log.e("Util", "Error while decrypting, at path: " + path);
            e.printStackTrace();
        }
        decryptError = true;
        return "";
    }

    public static String sha512(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] messageDigest = md.digest(input);
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder hashtext = new StringBuilder(no.toString(16));

            while (hashtext.length() < 32) {
                hashtext.insert(0, "0");
            }
            return hashtext.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String sha512sum_project(int id) {
        SketchwareProjects manager = new SketchwareProjects(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.sketchware/");
        AtomicReference<SketchwareProject> project_atomic = new AtomicReference<>();

        BuildersKt.launch(GlobalScope.INSTANCE,
                Dispatchers.getMain(), // Context to be ran on
                CoroutineStart.DEFAULT,
                (coroutineScope, continuation) -> {
                    Object return_value = manager.getProject(id, (Continuation<? super Project>) continuation);
                    project_atomic.set((SketchwareProject) return_value);
                    return return_value;
                }
        ).start();

        // Wait for the coroutine to finish
        while (project_atomic.get() == null) { }

        SketchwareProject project = project_atomic.get();

        try {
            FileInputStream file = new FileInputStream(project.getData().getFile());
            FileInputStream logic = new FileInputStream(project.getData().getLogic());
            FileInputStream library = new FileInputStream(project.getData().getLibrary());
            FileInputStream view = new FileInputStream(project.getData().getView());
            FileInputStream resource = new FileInputStream(project.getData().getResource());

            // Read all of them lmao
            byte[] file_ = readFile(file);
            byte[] logic_ = readFile(logic);
            byte[] library_ = readFile(library);
            byte[] view_ = readFile(view);
            byte[] resource_ = readFile(resource);

            byte[] joined = joinByteArrays(file_, joinByteArrays(logic_, joinByteArrays(library_, joinByteArrays(view_, resource_))));

            // Return the shasum
            return sha512(joined);
        } catch (IOException e) {
            // Pretty much impossible
            e.printStackTrace();
        }

        // Something wrong happened
        return null;
    }

    // Copied from: https://www.journaldev.com/9400/android-external-storage-read-write-save-file
    public static String readFile(String path) throws IOException {
        StringBuilder output = new StringBuilder();
        FileInputStream fis = new FileInputStream(path);
        DataInputStream in = new DataInputStream(fis);
        BufferedReader br =
                new BufferedReader(new InputStreamReader(in));
        String strLine;
        while ((strLine = br.readLine()) != null) {
            output.append(strLine);
        }
        in.close();
        return output.toString();
    }

    public static byte[] readFile(final FileInputStream stream) {
        class Reader extends Thread {
            byte[] array = null;
        }

        Reader reader = new Reader() {
            public void run() {
                LinkedList<Pair<byte[], Integer>> chunks = new LinkedList<Pair<byte[], Integer>>();

                // read the file and build chunks
                int size = 0;
                int globalSize = 0;
                do {
                    try {
                        int chunkSize = 8192;
                        // read chunk
                        byte[] buffer = new byte[chunkSize];
                        size = stream.read(buffer, 0, chunkSize);
                        if (size > 0) {
                            globalSize += size;

                            // add chunk to list
                            chunks.add(new Pair<byte[], Integer>(buffer, size));
                        }
                    } catch (Exception e) {
                        // very bad
                    }
                } while (size > 0);

                try {
                    stream.close();
                } catch (Exception e) {
                    // very bad
                }

                array = new byte[globalSize];

                // append all chunks to one array
                int offset = 0;
                for (Pair<byte[], Integer> chunk : chunks) {
                    // flush chunk to array
                    System.arraycopy(chunk.first, 0, array, offset, chunk.second);
                    offset += chunk.second;
                }
            }
        };

        reader.start();
        try {
            reader.join();
        } catch (InterruptedException e) {
            Log.e("Util", "Failed on reading file from storage while the locking Thread", e);
            return null;
        }

        return reader.array;
    }

    public static byte[] joinByteArrays(final byte[] array1, byte[] array2) {
        byte[] joinedArray = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
}
