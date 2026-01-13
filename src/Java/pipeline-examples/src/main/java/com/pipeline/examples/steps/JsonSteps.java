package com.pipeline.examples.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class JsonSteps {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private JsonSteps() {}
  public static String toJson(List<Map<String,String>> rows) {
    try {
      return OBJECT_MAPPER.writeValueAsString(rows);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
