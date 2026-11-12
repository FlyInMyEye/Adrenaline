package net.fly.adrenaline.scheduler;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class GenerationTaskQueue<T extends GenerationTaskQueue.Entry> implements Iterable<T> {

    private final ArrayDeque<T> entries = new ArrayDeque<>();

    public void addLast(T entry) {
        this.entries.addLast(entry);
    }

    public int size() {
        return this.entries.size();
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public void clear() {
        this.entries.clear();
    }

    @Override
    public Iterator<T> iterator() {
        return this.entries.iterator();
    }

    public T poll(Priority priority) {
        if (priority == Priority.FIFO || this.entries.isEmpty()) {
            return this.entries.pollFirst();
        }
        Iterator<T> iterator = this.entries.iterator();
        T selected = iterator.next();
        int highestStage = selected.stageIndex();
        long closestDistance = selected.proximity();
        while (iterator.hasNext()) {
            T candidate = iterator.next();
            int stage = candidate.stageIndex();
            long distance = candidate.proximity();
            boolean preferred = priority == Priority.HIGHEST
                ? stage > highestStage || stage == highestStage && distance < closestDistance
                : distance < closestDistance || distance == closestDistance && stage > highestStage;
            if (preferred) {
                selected = candidate;
                highestStage = stage;
                closestDistance = distance;
            }
        }
        this.entries.remove(selected);
        return selected;
    }

    public interface Entry {
        int stageIndex();

        long proximity();
    }

    public enum Priority {
        FIFO,
        HIGHEST,
        NEAREST
    }
}
