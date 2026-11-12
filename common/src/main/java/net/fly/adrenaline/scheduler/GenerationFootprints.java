package net.fly.adrenaline.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GenerationFootprints<T extends GenerationFootprints.Entry> {

    private final HashSet<Long> active = new HashSet<>();
    private final LinkedHashSet<T> waiting = new LinkedHashSet<>();
    private final HashMap<Long, Set<T>> waitIndex = new HashMap<>();

    public boolean isActive(long position) {
        return this.active.contains(position);
    }

    public boolean conflicts(Set<Long> footprint) {
        for (long key : footprint) {
            if (this.active.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public void acquire(Set<Long> footprint) {
        this.active.addAll(footprint);
    }

    public void addWaiting(T entry) {
        this.waiting.add(entry);
        for (long key : entry.footprint()) {
            this.waitIndex.computeIfAbsent(key, ignored -> new HashSet<>()).add(entry);
        }
    }

    public int waitingCount() {
        return this.waiting.size();
    }

    public void copyWaitingTo(Collection<? super T> entries) {
        entries.addAll(this.waiting);
    }

    public List<T> release(Set<Long> footprint) {
        this.active.removeAll(footprint);
        Set<T> candidates = new HashSet<>();
        for (long key : footprint) {
            Set<T> waiters = this.waitIndex.get(key);
            if (waiters != null) {
                candidates.addAll(waiters);
            }
        }
        List<T> ready = new ArrayList<>();
        Iterator<T> iterator = this.waiting.iterator();
        while (iterator.hasNext()) {
            T candidate = iterator.next();
            if (candidates.contains(candidate) && !this.conflicts(candidate.footprint())) {
                iterator.remove();
                this.removeFromIndex(candidate);
                ready.add(candidate);
            }
        }
        return ready;
    }

    public void clear() {
        this.active.clear();
        this.waiting.clear();
        this.waitIndex.clear();
    }

    private void removeFromIndex(T entry) {
        for (long key : entry.footprint()) {
            Set<T> waiters = this.waitIndex.get(key);
            if (waiters != null) {
                waiters.remove(entry);
                if (waiters.isEmpty()) {
                    this.waitIndex.remove(key);
                }
            }
        }
    }

    public interface Entry {
        Set<Long> footprint();
    }
}
