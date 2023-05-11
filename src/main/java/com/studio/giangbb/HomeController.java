package com.studio.giangbb;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by giangbb on 11/05/2023
 */
@Controller
public class HomeController {
    @RequestMapping("/")
    public ModelAndView welcome() {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("index.html");
        return modelAndView;
    }

    @RequestMapping("/client")
    public ModelAndView client() {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("client.html");
        return modelAndView;
    }
}
