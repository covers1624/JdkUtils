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
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.objectweb.asm.Opcodes.*;

/**
 * Generates a class which echos all the provided program arguments as system property Key-value pairs
 * to the program's standard output.
 * <pre>
 *     public static void main(String[] var0) {
 *         PrintStream var1 = System.out;
 *
 *         for(int var2 = 0; var2 < var0.length; ++var2) {
 *             var1.print(var0[var2]);
 *             var1.print("=");
 *             var1.println(System.getProperty(var0[var2], ""));
 *         }
 *     }
 * </pre>
 * When invoked with the following arguments <code>"java.version" "java.vendor"</code> the following
 * standard output is generated:
 * <pre>
 *     java.version=1.8.0_292
 *     java.vendor=Oracle Corporation
 * </pre>
 * <p>
 * Whilst this class requires Objectweb ASM, it is only required when re-generating the PropExtract class.
 * <p>
 * Created by covers1624 on 28/10/21.
 */
@Requires ("org.ow2.asm:asm")
public class JavaPropExtractGenerator {

    /**
     * The default list of properties extracted by this tool.
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
     * A pre-generated PropExtract class.
     */
    private static final byte[] DEFAULT_CLASS_BYTES = SneakyUtils.sneaky(() -> {
        InputStream is = JavaPropExtractGenerator.class.getResourceAsStream("/assets/PropExtract.class.bin");
        if (is == null) throw new ExceptionInInitializerError("Unable to locate 'PropExtract.class' asset.");
        try (InputStream _is = is) {
            return IOUtils.toBytes(_is);
        }
    });

    /**
     * Write a 'PropExtract.class' file into the current folder.
     *
     * @param folder The folder to write to.
     * @return The File which was created.
     * @throws IOException If an IO error occurs.
     */
    public static Path writeClass(Path folder) throws IOException {
        Path classFile = folder.resolve("PropExtract.class");
        Files.createDirectories(classFile.getParent());
        try (OutputStream os = Files.newOutputStream(classFile)) {
            os.write(DEFAULT_CLASS_BYTES);
            os.flush();
        }
        return classFile;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected single argument.");

        Path path = Paths.get(args[0]).toAbsolutePath();

        Files.createDirectories(path.getParent());
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(generateClass());
            os.flush();
        }
    }

    public static byte[] generateClass() {
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

        int array = 0;
        int out = 1;
        int index = 2;

        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitVarInsn(ASTORE, out);

        Label head = new Label();
        Label after = new Label();
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, index);
        mv.visitLabel(head);
        mv.visitVarInsn(ILOAD, index);
        mv.visitVarInsn(ALOAD, array);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitJumpInsn(IF_ICMPGE, after);

        mv.visitVarInsn(ALOAD, out);
        mv.visitVarInsn(ALOAD, array);
        mv.visitVarInsn(ILOAD, index);
        mv.visitInsn(AALOAD);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);

        mv.visitVarInsn(ALOAD, out);
        mv.visitLdcInsn("=");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);

        mv.visitVarInsn(ALOAD, out);
        mv.visitVarInsn(ALOAD, array);
        mv.visitVarInsn(ILOAD, index);
        mv.visitInsn(AALOAD);
        mv.visitLdcInsn("");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        mv.visitIincInsn(index, 1);
        mv.visitJumpInsn(GOTO, head);

        mv.visitLabel(after);
        mv.visitInsn(RETURN);
        mv.visitMaxs(4, 3);
        mv.visitEnd();
        return cw.toByteArray();
    }
}
