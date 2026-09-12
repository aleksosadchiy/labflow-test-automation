package com.mocklab.web;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sample List screen. Only {@code GET /samples} exists so far — no
 * {@code Sample} domain has been implemented yet (Slice 2), so the model
 * always carries an empty list and {@code sample-list.html} renders the
 * "No samples registered yet." empty state.
 */
@Controller
public class SampleWebController {

    @GetMapping("/samples")
    public String sampleList(Model model) {
        model.addAttribute("samples", Collections.emptyList());
        return "sample-list";
    }
}
