package com.example.emotion_backend;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/meme")
@CrossOrigin(origins = "*") // 允许 Vue 前端跨域访问
public class MemeController {

    // 模拟一个简单的数据库：情绪 -> 表情包数据
    private static final Map<String, Map<String, String>> MEME_DATABASE = new HashMap<>();

    static {
        // 开心情绪的配置
        Map<String, String> happyMeme = new HashMap<>();
        happyMeme.put("imgUrl", "https://img-blog.csdnimg.cn/2021032415174092.jpg"); // 这里先随便放张网图，后续换成你的OSS或本地图
        happyMeme.put("text", "【后端返回】快乐就完事了，奥利给！");
        MEME_DATABASE.put("happy", happyMeme);

        // 惊讶情绪的配置
        Map<String, String> surprisedMeme = new HashMap<>();
        surprisedMeme.put("imgUrl", "https://img-blog.csdnimg.cn/2021032415174092.jpg");
        surprisedMeme.put("text", "【后端返回】我的天呐，居然被后端写出来了！");
        MEME_DATABASE.put("surprised", surprisedMeme);
        
        // 默认兜底配置
        Map<String, String> defaultMeme = new HashMap<>();
        defaultMeme.put("imgUrl", "");
        defaultMeme.put("text", "【后端返回】检测到了情绪，但后端还在继续努力开发中...");
        MEME_DATABASE.put("default", defaultMeme);
    }

    @GetMapping("/match")
    public Map<String, String> matchMeme(@RequestParam String emotion) {
        // 根据前端传来的 emotion 参数，去 map 里匹配
        return MEME_DATABASE.getOrDefault(emotion, MEME_DATABASE.get("default"));
    }
}