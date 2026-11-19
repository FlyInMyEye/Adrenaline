package net.fly.adrenaline.worldgen;

public interface AdrenalineSectionNoiseAccess {

    boolean adrenaline$supportsSectionTraversal(int minCellY, int cellCountY);

    NativeDensityProgram adrenaline$sectionDensityProgram();

    void adrenaline$setSectionCellX(int cellX);
}
