/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class App {
    public static void main(String[] args) {

        Outfit outfit = new Outfit(130, 0, 1, 3, 406, "FFFFFF", "FFFFFF", "FFFFFF", "FFFFFF", false);
        
        OutfitPaths paths = SpritePathBuilder.getAllPaths(outfit);

        try {
            // Mesclar as imagens
            BufferedImage mergedImage = ImageMerger.mergeImages(paths, outfit);

            // Salvar a imagem resultante
            String outputPath = "TibiaWalk/app/src/main/resources/mergedOutput/merged_outfit.png";
            ImageMerger.saveImage(mergedImage, outputPath);

            System.out.println("Imagem mesclada salva em: " + outputPath);
        } catch (IOException e) {
            System.err.println("Erro ao mesclar as imagens: " + e.getMessage());
        }

    }
}
