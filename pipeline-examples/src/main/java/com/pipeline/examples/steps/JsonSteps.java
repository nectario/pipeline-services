package com.pipeline.examples.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class JsonSteps {
  private static final ObjectMapper M = new ObjectMapper();
  private JsonSteps() {}
  public static String toJson(List<Map<String,String>> rows) throws Exception { return M.writeValueAsString(rows); }
}

