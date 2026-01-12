package com.pipeline.examples.steps;

import java.util.*;

public final class CsvSteps {
  private CsvSteps() {}
  /** Very small CSV parser for "a,b\n1,2" -> List<Map> with headers. */
  public static List<Map<String,String>> parse(String csv) {
    List<Map<String,String>> out = new ArrayList<>();
    if (csv == null || csv.isBlank()) return out;
    String[] lines = csv.strip().split("\\R+");
    String[] headers = lines[0].split(",");
    for (int i=1; i<lines.length; i++) {
      String[] vals = lines[i].split(",");
      Map<String,String> row = new LinkedHashMap<>();
      for (int j=0; j<headers.length && j<vals.length; j++) row.put(headers[j].trim(), vals[j].trim());
      out.add(row);
    }
    return out;
  }
}
