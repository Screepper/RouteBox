package com.routebox.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @Description List Tool
 * @Date 2025/11/24
**/
public class ListUtil {
    /**
     * @Description Check if two lists are different
     * @Date 2025/7/26
     **/
    public static boolean checkDiffList(List<Integer> list1, List<Integer> list2) {
        if (list1.size() != list2.size()) return true;

        List<Integer> copy1 = new ArrayList<>(list1);
        List<Integer> copy2 = new ArrayList<>(list2);
        Collections.sort(copy1);
        Collections.sort(copy2);

        return !copy1.equals(copy2);
    }

    /**
     * @Description Get a safe immutable list, return an empty list if the input list is null
     * @Date 2025/11/24
     **/
    public static <T> List<T> getSafeImmutableList(List<T> list) {
        return Objects.isNull(list) ? Collections.emptyList() : list;
    }

    /**
     * @Description Get a safe mutable list, return an empty list if the input list is null
     * @Date 2025/11/24
     **/
    public static <T> List<T> getSafeMutableList(List<T> list) {
        return Objects.isNull(list) ? new ArrayList<>() : list;
    }
}
