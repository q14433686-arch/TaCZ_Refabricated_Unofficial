package com.tacz.guns.crafting;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Allocate counted recipe inputs to actual slots before touching the inventory. A slot may
 * match several ingredients (e.g. a named item and a tag), but each item pays only once. */
public final class MaterialAllocation {
    private MaterialAllocation() {}

    public interface Match { boolean accepts(int ingredient, int slot); }

    /** Per-slot deductions or null if the entire bill cannot be paid. Neither input is mutated. */
    public static int[] plan(int[] demand, int[] stock, Match match) {
        int[] free = stock.clone();
        if (demand.length == 0 || Arrays.stream(demand).anyMatch(n -> n <= 0)
                || Arrays.stream(demand).asLongStream().sum() > Arrays.stream(stock).asLongStream().sum()) return null;
        int[][] assigned = new int[demand.length][stock.length];
        boolean[][] accepts = new boolean[demand.length][stock.length];
        for (int i = 0; i < demand.length; i++) {
            for (int s = 0; s < stock.length; s++) accepts[i][s] = stock[s] > 0 && match.accepts(i, s);
            for (int left = demand[i]; left > 0;) {
                // Find more stock, rerouting a broader earlier ingredient if necessary.
                var queue = new ArrayDeque<Integer>();
                int[] fromSlot = new int[i + 1], fromInput = new int[stock.length];
                Arrays.fill(fromSlot, -1);
                Arrays.fill(fromInput, -1);
                queue.add(i);
                int end = -1;
                while (!queue.isEmpty() && end < 0) {
                    int input = queue.removeFirst();
                    for (int s = 0; s < stock.length; s++) {
                        if (fromInput[s] != -1 || !accepts[input][s]) continue;
                        fromInput[s] = input;
                        if (free[s] > 0) { end = s; break; }
                        for (int other = 0; other < i; other++) {
                            if (fromSlot[other] == -1 && assigned[other][s] > 0) {
                                fromSlot[other] = s;
                                queue.addLast(other);
                            }
                        }
                    }
                }
                if (end < 0) return null;
                int amount = Math.min(left, free[end]);
                for (int m = fromInput[end]; m != i; m = fromInput[fromSlot[m]]) {
                    amount = Math.min(amount, assigned[m][fromSlot[m]]);
                }
                free[end] -= amount;
                left -= amount;
                for (int s = end;;) {
                    int m = fromInput[s];
                    assigned[m][s] += amount;
                    if (m == i) break;
                    s = fromSlot[m];
                    assigned[m][s] -= amount;
                }
            }
        }
        for (int s = 0; s < stock.length; s++) free[s] = stock[s] - free[s];
        return free;
    }
}
