package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.core.ShortCircuit;
import com.pipeline.core.Steps;

public final class Main {
    public static void main(String[] args) {

        var p = Pipeline.build("clean_text", false,
                (String s) -> s.strip(),
                Steps.ignoreErrors((String s) -> riskyNormalize(s)),
                (String s) -> s.length() > 40 ? ShortCircuit.now(s.substring(0, 40)) : s
        );
        System.out.println(p.run("  Hello   <b>World</b>  "));
    }

    static String riskyNormalize(String s) {
        // toy example; replace tags with space
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}

