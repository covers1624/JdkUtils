/*
 * This file is part of JdkUtils and is Licensed under the MIT License.
 *
 * Copyright (c) 2018-2021 covers1624 <https://github.com/covers1624>
 */
package net.covers1624.jdkutils;

import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.util.SneakyUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.objectweb.asm.Opcodes.*;

/**
 * Generates a Java class to extract Key-value pairs of properties.
 * <p>
 * Even though this class is annotated as requiring Obejctweb asm. It will only be required
 * when generating anything other than {@link #DEFAULTS}.
 * <p>
 * Created by covers1624 on 28/10/21.
 */
@Requires ("org.ow2.asm:asm")
public class JavaPropExtractGenerator {

    /**
     * The list of properties extracted by this tool.
     * <p>
     * NOTE: Whenever this array is modified, the '/assets/PropExtract.class.bin' asset
     * will need to be updated.
     */
    public static String[] DEFAULTS = {
            "java.home",
            "java.version",
            "java.vendor",
            "os.arch",
            "java.vm.name",
            "java.vm.version",
            "java.runtime.name",
            "java.runtime.version",
            "java.class.version",
    };

    /**
     * A pre-generated class containing {@link #DEFAULTS} to be extracted.
     */
    private static final byte[] DEFAULT_CLASS_BYTES = SneakyUtils.sneaky(() -> {
        InputStream is = JavaPropExtractGenerator.class.getResourceAsStream("/assets/PropExtract.class.bin");
        if (is == null) throw new ExceptionInInitializerError("Unable to locate 'PropExtract.class' asset.");
        try (InputStream _is = is) {
            return IOUtils.toBytes(_is);
        }
    });

    /**
     * Write a class with {@link #DEFAULTS} properties to be extracted and write
     * it to the provided folder with the name 'PropExtract.class'.
     *
     * @param folder The folder to write to.
     * @return The File which was created.
     * @throws IOException If an IO error occurs.
     */
    public static Path writeClass(Path folder) throws IOException {
        return writeClass(folder, DEFAULTS);
    }

    /**
     * Write a class with <code>properties</code> to be extracted and write
     * it to the provided folder with the name 'PropExtract.class'.
     *
     * @param folder The folder to write to.
     * @return The File which was created.
     * @throws IOException If an IO error occurs.
     */
    public static Path writeClass(Path folder, String[] properties) throws IOException {
        Path classFile = folder.resolve("PropExtract.class");
        Files.createDirectories(classFile.getParent());
        try (OutputStream os = Files.newOutputStream(classFile)) {
            os.write(properties == DEFAULTS ? DEFAULT_CLASS_BYTES : generateClass(properties));
            os.flush();
        }
        return classFile;
    }

    /**
     * Generates a minimal Java class compatible with any JVM version to
     * export the configured properties.
     *
     * @return The class bytes.
     */
    public static byte[] generateClass(String[] properties) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_1, ACC_PUBLIC | ACC_SUPER, "PropExtract", null, "java/lang/Object", null);
        MethodVisitor mv;

        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();
        for (String property : properties) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn(property + "=");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);

            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn(property);
            mv.visitLdcInsn("");
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
        return cw.toByteArray();
    }
}
