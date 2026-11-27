package com.tacz.guns.crafting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialAllocationTest {
    @Test void oneSlotCanPayTwoIngredientsButNotTwiceItsStock() {
        assertArrayEquals(new int[]{5}, MaterialAllocation.plan(new int[]{2,3}, new int[]{5}, (i,s) -> true));
        assertNull(MaterialAllocation.plan(new int[]{2,3}, new int[]{4,1}, (i,s) -> s == 0));
    }
    @Test void broadPredicateCanMoveOffAnItemRequiredByALaterNarrowPredicate() {
        int[] stock = {3,2,4};
        assertArrayEquals(new int[]{3,2,3}, MaterialAllocation.plan(new int[]{5,3}, stock, (i,s) -> i == 0 || s == 0));
        assertArrayEquals(new int[]{3,2,4}, stock);
    }
    @Test void invalidAndUnfundedInputsDontProduceAPlan() {
        assertNull(MaterialAllocation.plan(new int[]{0}, new int[]{1}, (i,s) -> true));
        assertNull(MaterialAllocation.plan(new int[]{Integer.MAX_VALUE,1}, new int[]{Integer.MAX_VALUE}, (i,s) -> true));
    }
}
