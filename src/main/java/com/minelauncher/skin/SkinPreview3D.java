package com.minelauncher.skin;

import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Renderizador 3D do personagem Minecraft com a skin aplicada.
 * <p>
 * O modelo é composto de 6 partes (cabeça, corpo, braços e pernas),
 * cada uma como um {@link TriangleMesh} com mapeamento UV seguindo
 * o layout oficial da skin do Minecraft (64×64).
 * <p>
 * Suporte a rotação por mouse drag e zoom com scroll.
 * <p>
 * Uso:
 * <pre>{@code
 * SkinPreview3D preview = new SkinPreview3D(skinImage);
 * somePane.getChildren().add(preview.getSubScene());
 * }</pre>
 */
public class SkinPreview3D {

    private static final double SCALE = 5.0;       // escala dos blocos
    private static final double INITIAL_Z = -60;

    // Dimensões em pixels do modelo Minecraft (1 pixel = 1 unidade)
    private static final double HEAD_W = 8, HEAD_H = 8, HEAD_D = 8;
    private static final double BODY_W = 8, BODY_H = 12, BODY_D = 4;
    private static final double ARM_W = 4, ARM_H = 12, ARM_D = 4;
    private static final double LEG_W = 4, LEG_H = 12, LEG_D = 4;

    private static final double TEX_SIZE = 64.0; // skin 64×64

    private final Group root;
    private final PerspectiveCamera camera;
    private final SubScene subScene;

    private double mouseX, mouseY;
    private double rotX = -15, rotY = 30;

    public SkinPreview3D(Image skinTexture, double width, double height) {
        // Grupo principal que será rotacionado
        root = new Group();

        // Câmera
        camera = new PerspectiveCamera(true);
        camera.setNearClip(1);
        camera.setFarClip(1000);
        camera.getTransforms().add(new Translate(0, 0, INITIAL_Z));

        // SubScene
        subScene = new SubScene(root, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        subScene.setCamera(camera);

        // Build do modelo
        buildModel(skinTexture);

        // Controles de mouse
        setupMouseControls();
    }

    /** Atualiza a textura da skin sem recriar o SubScene. */
    public void updateSkin(Image newSkin) {
        root.getChildren().clear();
        buildModel(newSkin);
    }

    public SubScene getSubScene() {
        return subScene;
    }

    public Group getRoot() {
        return root;
    }

    // ─────────────────────────────────────────────────────────────
    //  Construção do modelo
    // ─────────────────────────────────────────────────────────────

    private void buildModel(Image skin) {
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(skin);

        // Escala geral
        double s = SCALE;

        // Cabeça (centro no topo)
        // centro x=0, y = -(BODY_H/2 + HEAD_H/2)*s
        double headY = -(BODY_H / 2 + HEAD_H / 2) * s;
        addPart(root, material,
                HEAD_W, HEAD_H, HEAD_D,
                0, headY, 0,
                // UV: cabeça no formato clássico 64x64
                // Frente (8,8)-(16,16)
                new FaceUV(8, 8, 16, 16),
                // Costas (24,8)-(32,16)
                new FaceUV(24, 8, 32, 16),
                // Topo (8,0)-(16,8)
                new FaceUV(8, 0, 16, 8),
                // Baixo (16,0)-(24,8)
                new FaceUV(16, 0, 24, 8),
                // Direita (0,8)-(8,16)
                new FaceUV(0, 8, 8, 16),
                // Esquerda (16,8)-(24,16)
                new FaceUV(16, 8, 24, 16));

        // Corpo
        addPart(root, material,
                BODY_W, BODY_H, BODY_D,
                0, 0, 0,
                new FaceUV(20, 20, 28, 32),   // frente
                new FaceUV(32, 20, 40, 32),   // costas
                new FaceUV(20, 16, 28, 20),   // topo
                new FaceUV(28, 16, 36, 20),   // baixo
                new FaceUV(16, 20, 20, 32),   // direita
                new FaceUV(28, 20, 32, 32));  // esquerda

        // Braço direito (lado direito do corpo)
        double armX = (BODY_W / 2 + ARM_W / 2) * s;
        addPart(root, material,
                ARM_W, ARM_H, ARM_D,
                armX, 0, 0,
                new FaceUV(44, 20, 48, 32),   // frente
                new FaceUV(52, 20, 56, 32),   // costas
                new FaceUV(44, 16, 48, 20),   // topo
                new FaceUV(48, 16, 52, 20),   // baixo
                new FaceUV(40, 20, 44, 32),   // direita
                new FaceUV(48, 20, 52, 32));  // esquerda

        // Braço esquerdo (lado esquerdo do corpo) — usa overlay da skin (clássico 64x64)
        double armLX = -(BODY_W / 2 + ARM_W / 2) * s;
        addPart(root, material,
                ARM_W, ARM_H, ARM_D,
                armLX, 0, 0,
                new FaceUV(36, 52, 40, 64),   // frente
                new FaceUV(44, 52, 48, 64),   // costas
                new FaceUV(36, 48, 40, 52),   // topo
                new FaceUV(40, 48, 44, 52),   // baixo
                new FaceUV(32, 52, 36, 64),   // direita
                new FaceUV(40, 52, 44, 64));  // esquerda

        // Perna direita
        double legY = -(BODY_H / 2 + LEG_H / 2) * s;
        double legX = (LEG_W / 2) * s;
        addPart(root, material,
                LEG_W, LEG_H, LEG_D,
                legX, legY, 0,
                new FaceUV(4, 20, 8, 32),    // frente
                new FaceUV(12, 20, 16, 32),  // costas
                new FaceUV(4, 16, 8, 20),    // topo
                new FaceUV(8, 16, 12, 20),   // baixo
                new FaceUV(0, 20, 4, 32),    // direita
                new FaceUV(8, 20, 12, 32));  // esquerda

        // Perna esquerda — usa overlay (clássico 64x64)
        double legLX = -(LEG_W / 2) * s;
        addPart(root, material,
                LEG_W, LEG_H, LEG_D,
                legLX, legY, 0,
                new FaceUV(20, 52, 24, 64),  // frente
                new FaceUV(28, 52, 32, 64),  // costas
                new FaceUV(20, 48, 24, 52),  // topo
                new FaceUV(24, 48, 28, 52),  // baixo
                new FaceUV(16, 52, 20, 64),  // direita
                new FaceUV(24, 52, 28, 64)); // esquerda
    }

    /**
     * Cria um cubo texturizado e adiciona ao grupo.
     */
    private void addPart(Group group, PhongMaterial material,
                         double w, double h, double d,
                         double posX, double posY, double posZ,
                         FaceUV front, FaceUV back, FaceUV top,
                         FaceUV bottom, FaceUV right, FaceUV left) {
        double sx = SCALE, sy = SCALE, sz = SCALE;
        double hw = w / 2.0, hh = h / 2.0, hd = d / 2.0;

        // 8 vértices (coordenadas relativas ao centro da peça)
        float[] points = new float[]{
            (float)(-hw * sx), (float)(-hh * sy), (float)(-hd * sz), // 0
            (float)( hw * sx), (float)(-hh * sy), (float)(-hd * sz), // 1
            (float)( hw * sx), (float)( hh * sy), (float)(-hd * sz), // 2
            (float)(-hw * sx), (float)( hh * sy), (float)(-hd * sz), // 3
            (float)(-hw * sx), (float)(-hh * sy), (float)( hd * sz), // 4
            (float)( hw * sx), (float)(-hh * sy), (float)( hd * sz), // 5
            (float)( hw * sx), (float)( hh * sy), (float)( hd * sz), // 6
            (float)(-hw * sx), (float)( hh * sy), (float)( hd * sz), // 7
        };

        // UV coordinates — 4 por face, normalizadas para [0,1]
        float[] uvs = new float[6 * 4 * 2]; // 6 faces × 4 cantos × 2 coordenadas

        FaceUV[] faces = new FaceUV[]{front, back, top, bottom, right, left};
        int uvIdx = 0;
        for (FaceUV f : faces) {
            float u0 = f.u0 / (float)TEX_SIZE;
            float v0 = f.v0 / (float)TEX_SIZE;
            float u1 = f.u1 / (float)TEX_SIZE;
            float v1 = f.v1 / (float)TEX_SIZE;
            // top-left, top-right, bottom-right, bottom-left
            uvs[uvIdx++] = u0; uvs[uvIdx++] = v0;
            uvs[uvIdx++] = u1; uvs[uvIdx++] = v0;
            uvs[uvIdx++] = u1; uvs[uvIdx++] = v1;
            uvs[uvIdx++] = u0; uvs[uvIdx++] = v1;
        }

        // Faces: 2 triângulos por face, 6 faces = 12 triângulos
        // Formato: [p1, uv1, p2, uv2, p3, uv3]
        int[] faceIndices = new int[12 * 6];

        // Mapeamento: face 0=frente, 1=costas, 2=topo, 3=baixo, 4=direita, 5=esquerda
        // Cada face tem 4 vértices (a, b, c, d) = 2 triângulos (a,b,c) e (a,c,d)
        int[][] faceVerts = new int[][]{
            {4, 5, 6, 7},  // frente
            {1, 0, 3, 2},  // costas
            {7, 6, 2, 3},  // topo
            {0, 1, 5, 4},  // baixo
            {5, 1, 2, 6},  // direita
            {0, 4, 7, 3},  // esquerda
        };

        int fi = 0;
        for (int f = 0; f < 6; f++) {
            int vA = faceVerts[f][0];
            int vB = faceVerts[f][1];
            int vC = faceVerts[f][2];
            int vD = faceVerts[f][3];
            int uvBase = f * 4;

            // Triângulo 1: a, b, c
            faceIndices[fi++] = vA; faceIndices[fi++] = uvBase;
            faceIndices[fi++] = vB; faceIndices[fi++] = uvBase + 1;
            faceIndices[fi++] = vC; faceIndices[fi++] = uvBase + 2;

            // Triângulo 2: a, c, d
            faceIndices[fi++] = vA; faceIndices[fi++] = uvBase;
            faceIndices[fi++] = vC; faceIndices[fi++] = uvBase + 2;
            faceIndices[fi++] = vD; faceIndices[fi++] = uvBase + 3;
        }

        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(uvs);
        mesh.getFaces().setAll(faceIndices);

        // Calcular normais automaticamente
        mesh.getFaceSmoothingGroups().setAll(new int[12]); // all same group

        MeshView meshView = new MeshView(mesh);
        meshView.setMaterial(material);

        // Posicionar
        meshView.setTranslateX(posX);
        meshView.setTranslateY(posY);
        meshView.setTranslateZ(posZ);

        group.getChildren().add(meshView);
    }

    // ─────────────────────────────────────────────────────────────
    //  Controles de mouse
    // ─────────────────────────────────────────────────────────────

    private void setupMouseControls() {
        Rotate xRotate = new Rotate(rotX, Rotate.X_AXIS);
        Rotate yRotate = new Rotate(rotY, Rotate.Y_AXIS);
        root.getTransforms().addAll(yRotate, xRotate);

        subScene.setOnMousePressed((MouseEvent e) -> {
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
        });

        subScene.setOnMouseDragged((MouseEvent e) -> {
            double dx = e.getSceneX() - mouseX;
            double dy = e.getSceneY() - mouseY;
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();

            rotY += dx * 0.6;
            rotX += dy * 0.6;
            rotX = Math.max(-90, Math.min(90, rotX));

            yRotate.setAngle(rotY);
            xRotate.setAngle(rotX);
        });

        subScene.setOnScroll((ScrollEvent e) -> {
            double delta = e.getDeltaY() * 0.5;
            double z = camera.getTranslateZ();
            z += delta;
            z = Math.max(-150, Math.min(-20, z));
            camera.setTranslateZ(z);
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  Helper: região UV de uma face
    // ─────────────────────────────────────────────────────────────

    private record FaceUV(int u0, int v0, int u1, int v1) {}
}
