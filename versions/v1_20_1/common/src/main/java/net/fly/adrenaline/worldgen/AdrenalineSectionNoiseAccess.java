package net.fly.adrenaline.worldgen;

public interface AdrenalineSectionNoiseAccess {

    boolean adrenaline$supportsSectionTraversal(int minCellY, int cellCountY);

    NativeDensityProgram adrenaline$sectionDensityProgram();

    boolean adrenaline$selectSectionCell(int cellY, int cellZ);

    void adrenaline$setSectionCellX(int cellX);
}
