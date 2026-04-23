package com.example.images;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;

import javax.imageio.ImageIO;

public class ImageProcessor {

    public static void main(String[] args) {
        // First we generate 100 images from source_images folder
        try {
            multiplyImages("source_images", "sample_images", 100);
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            e.printStackTrace();
        }

        // Simulate processing a batch of images
        try {
            processImageFolder("sample_images", "processed_images");
        } catch (OutOfMemoryError e) {
            System.err.println("Out of memory error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void multiplyImages(
            String inputFolder, String outputFolder, int multiplicationFactor) throws IOException {
        File folder = new File(inputFolder);
        File[] imageFiles =
                folder.listFiles(
                        (dir, name) ->
                                name.toLowerCase().endsWith(".jpg")
                                        || name.toLowerCase().endsWith(".png"));

        if (imageFiles == null || imageFiles.length == 0) {
            System.out.println("No images found in the folder");
            return;
        }

        File outputDir = new File(outputFolder);
        if (!outputDir.exists()) outputDir.mkdirs();

        for (File imageFile : imageFiles) {
            String fileName = imageFile.getName();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String extension = fileName.substring(fileName.lastIndexOf('.'));

            for (int i = 1; i <= multiplicationFactor; i++) {
                String newFileName = String.format("%s_%d%s", baseName, i, extension);
                File outputFile = new File(outputDir, newFileName);
                try {
                    Files.copy(
                            imageFile.toPath(),
                            outputFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Error copying file: " + fileName + " - " + e.getMessage());
                }
            }
        }
    }

    public static void processImageFolder(String inputFolder, String outputFolder)
            throws IOException {
        Runtime runtime = Runtime.getRuntime();

        BiConsumer<String, Runtime> printMemoryStats =
                (stage, rt) -> {
                    long usedMemory = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                    long freeMemory = rt.freeMemory() / (1024 * 1024);
                    long totalMemory = rt.totalMemory() / (1024 * 1024);
                    long maxMemory = rt.maxMemory() / (1024 * 1024);

                    System.out.println("\n=== Memory Stats at " + stage + " ===");
                    System.out.println("Used Memory: " + usedMemory + " MB");
                    System.out.println("Free Memory: " + freeMemory + " MB");
                    System.out.println("Total Memory: " + totalMemory + " MB");
                    System.out.println("Maximum Memory: " + maxMemory + " MB");
                    System.out.println("==============================\n");
                };

        printMemoryStats.accept("START", runtime);

        File folder = new File(inputFolder);
        File[] imageFiles =
                folder.listFiles(
                        (dir, name) ->
                                name.toLowerCase().endsWith(".jpg")
                                        || name.toLowerCase().endsWith(".png"));

        if (imageFiles == null || imageFiles.length == 0) {
            System.out.println("No images found in the folder");
            return;
        }

        File outputDir = new File(outputFolder);
        if (!outputDir.exists()) outputDir.mkdirs();

        // STREAM PATTERN FIX: process one image at a time.
        // Load → process → save → release, then move to the next.
        // Peak memory = ~2 images at a time regardless of batch size.
        System.out.println("Processing images one at a time...");

        for (File imageFile : imageFiles) {
            // Load one image
            BufferedImage original = ImageIO.read(imageFile);

            // Process it immediately
            BufferedImage processed = applyEffects(original);

            // Save it immediately
            String outputName = outputFolder + File.separator + "processed_" + imageFile.getName();
            ImageIO.write(processed, getImageFormat(imageFile.getName()), new File(outputName));

            System.out.println("Processed: " + imageFile.getName());

            // original and processed go out of scope here.
            // GC can reclaim both before the next iteration loads.
        }

        // Single memory snapshot after all images are done.
        // Expected: much lower than the batch version's 1,438 MB peak.
        printMemoryStats.accept("END", runtime);

        System.out.println("All images processed successfully");
    }

    private static BufferedImage applyEffects(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage processed = new BufferedImage(width, height, original.getType());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = original.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xff;
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                int gray = (red + green + blue) / 3;
                int newRGB = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
                processed.setRGB(x, y, newRGB);
            }
        }

        return processed;
    }

    private static String getImageFormat(String filename) {
        return filename.toLowerCase().endsWith(".png") ? "png" : "jpeg";
    }
}

