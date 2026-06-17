package com.minelauncher.skin;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * Renderizador de skin que utiliza a API do Crafatar para gerar o modelo 3D.
 */
public class SkinPreview3D {

    private final ImageView imageView;
    private final StackPane stackPane;

    public SkinPreview3D(Image defaultSkin, double width, double height) {
        imageView = new ImageView(defaultSkin);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(true);

        stackPane = new StackPane(imageView);
    }

    /** Atualiza a imagem com base na nova skin, usando Crafatar para render 3D. */
    public void updateSkin(Image newSkin) {
        imageView.setImage(newSkin);
    }

    /** Carrega um render 3D do corpo do jogador usando o Crafatar. */
    public void render3D(String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        // O Crafatar gera o render 3D a partir do UUID da conta Minecraft
        String url = "https://crafatar.com/renders/body/" + uuid + "?overlay&size=340";
        imageView.setImage(new Image(url, true));
    }

    public StackPane getSubScene() {
        return stackPane;
    }
}
