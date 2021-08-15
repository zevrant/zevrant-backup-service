package com.zevrant.services.zevrantbackupservice.comparators;

import java.util.Comparator;

public class VersionComparator implements Comparator<String> {
    @Override
    public int compare(String s, String t1) {
        if (s.equals(t1)) {
            return 0;
        }
        String[] x = s.split("\\.");
        String[] y = t1.split("\\.");
        for (int i = 0; i < x.length; i++) {
            if (x[i].equals(y[i])) {
                continue;
            }
            if (Integer.parseInt(x[i]) < Integer.parseInt(y[i])) {
                return -1;
            } else {
                return 1;
            }
        }
        return 0;
    }
}
