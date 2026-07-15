<template>
  <div class="container">
    <h1>🎭 情绪变表情包 - 终极版</h1>
    
    <div class="status-bar" :class="{ ready: isModelLoaded }">
      {{ isModelLoaded ? '🟢 情绪雷达已就绪！' : '🟡 正在努力加载人脸识别模型中...' }}
    </div>

    <div class="main-layout">
      <!-- 左侧：摄像头画面 -->
      <div class="video-container">
        <h3>1. 捕捉你的表情</h3>
        <div class="video-box">
          <video 
            ref="videoRef" 
            autoplay 
            muted 
            playsinline 
            @play="onVideoPlay"
          ></video>
        </div>
        <div v-if="!hasCamera" class="camera-error">
          ⚠️ 未检测到摄像头，或请允许浏览器访问摄像头权限。
        </div>
        
        <!-- 实时检测到的情绪挂件 -->
        <div class="emotion-badge" v-if="currentEmotion" :class="currentEmotion">
          {{ emotionEmoji }} {{ translationMap[currentEmotion] }}
        </div>
      </div>

      <!-- 右侧：表情包生成区 -->
      <div class="result-container">
        <h3>2. 自动生成的表情包</h3>
        
        <!-- 隐藏的Canvas，专门用来画图 -->
        <canvas ref="canvasRef" width="300" height="300" style="display: none;"></canvas>

        <!-- 展示给用户看的可保存图片 -->
        <div class="meme-preview-box">
          <img v-if="memeResultUrl" :src="memeResultUrl" alt="生成的表情包" class="meme-img" />
          <div v-else class="placeholder-text">
            对着摄像头换个表情<br/>点击下方按钮生成专属表情包
          </div>
        </div>

        <button 
          @click="generateMemeImage" 
          :disabled="!currentEmotion" 
          class="generate-btn"
          :class="{ active: currentEmotion }"
        >
          🔥 瞬间凝结成表情包
        </button>

        <p class="tip" v-if="memeResultUrl">💡 提示：在图片上点击右键可以复制或另存为哦！</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import * as faceapi from '@vladmandic/face-api';
import axios from 'axios';

const videoRef = ref(null);
const canvasRef = ref(null);
const isModelLoaded = ref(false);
const hasCamera = ref(true);
const currentEmotion = ref(''); 
const backendMemeText = ref('');
const memeResultUrl = ref(''); // 存储 Canvas 生成的图片 Base64 地址

// 缓存当前检测到的人脸框位置，用来裁剪头像
let lastBox = null;

const emotionEmojiMap = { happy: '😄', sad: '😢', angry: '😡', surprised: '😲', fearful: '😨', disgusted: '🤢', neutral: '😐' };
const translationMap = { happy: '开心', sad: '难过', angry: '生气', surprised: '惊讶', fearful: '害怕', disgusted: '嫌弃', neutral: '平静' };

const emotionEmoji = computed(() => emotionEmojiMap[currentEmotion.value] || '🔍');

// 1. 加载模型
async function loadModels() {
  try {
    const MODEL_URL = '/models';
    await faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL);
    await faceapi.nets.faceExpressionNet.loadFromUri(MODEL_URL);
    isModelLoaded.value = true;
    startVideo();
  } catch (error) {
    console.error('模型加载失败：', error);
  }
}

// 2. 启动摄像头
async function startVideo() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ 
      video: { width: 400, height: 300 } 
    });
    if (videoRef.value) videoRef.value.srcObject = stream;
  } catch (err) {
    hasCamera.value = false;
  }
}

// 3. 循环监听情绪变化
function onVideoPlay() {
  const video = videoRef.value;
  if (!video) return;

  setInterval(async () => {
    if (!isModelLoaded.value) return;

    const detection = await faceapi
      .detectSingleFace(video, new faceapi.TinyFaceDetectorOptions())
      .withFaceExpressions();

    if (detection && detection.expressions) {
      // 记录人脸框位置
      lastBox = detection.detection.box;

      const expressions = detection.expressions;
      const highest = Object.keys(expressions).reduce((a, b) => expressions[a] > expressions[b] ? a : b);
      
      if (expressions[highest] > 0.5 && highest !== currentEmotion.value) {
        currentEmotion.value = highest;
        try {
          // 请求后端接口获取台词
          // const response = await axios.get(`http://localhost:8080/api/meme/match?emotion=${highest}`);
          const response = await axios.get(`/api/meme/match?emotion=${highest}`);
          backendMemeText.value = response.data.text;
        } catch (error) {
          backendMemeText.value = "【后端连接失败，但表情包依然可以生成】";
        }
      }
    } else {
      // 没人脸时不清空上一张表情包，方便用户点击
    }
  }, 200);
}

// 🎨 4. 核心：用 Canvas 把人脸和文字拼起来！
function generateMemeImage() {
  const video = videoRef.value;
  const canvas = canvasRef.value;
  if (!video || !canvas || !lastBox) return;

  const ctx = canvas.getContext('2d');
  
  // 1) 清空画布并刷上一层魔性的熊猫头式白色背景
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // 2) 经典黑框表情包描边
  ctx.strokeStyle = '#000000';
  ctx.lineWidth = 6;
  ctx.strokeRect(3, 3, canvas.width - 6, canvas.height - 6);

  // 3) 裁剪摄像头里用户的人脸，并画到 Canvas 上
  // 因为摄像头是镜像的，我们在这里做个水平反转确保裁剪出来的脸是正的
  ctx.save();
  ctx.translate(canvas.width, 0);
  ctx.scale(-1, 1);
  
  // 参数含义：从 video 里的人脸方框(lastBox)位置，裁剪并缩放到 canvas 居中区域
  ctx.drawImage(
    video,
    lastBox.x, lastBox.y, lastBox.width, lastBox.height, // 源切片位置
    50, 30, 200, 200 // 目标画布位置（留出下方写字的空间）
  );
  ctx.restore();

  // 4) 加个“黑白滤镜”让它更像魔性表情包（可选，这里先用彩色保持生动，后续你可以加黑白效果）

  // 5) 绘制从后端拿来的魔性文字
  ctx.fillStyle = '#000000'; // 纯黑字
  ctx.font = 'bold 16px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  
  // 表情包配文（支持长文本换行，这里简单处理在一行）
  let textToDraw = backendMemeText.value || "差不多已经是个废人了";
  ctx.fillText(textToDraw, canvas.width / 2, 260);

  // 6) 将 Canvas 导出为图片给 img 标签展示
  memeResultUrl.value = canvas.toDataURL('image/png');
}

onMounted(() => {
  loadModels();
});
</script>

<style scoped>
.container {
  max-width: 950px;
  margin: 0 auto;
  padding: 20px;
  font-family: 'PingFang SC', system-ui, sans-serif;
  text-align: center;
  color: #2c3e50;
}
.status-bar {
  background: #f1f3f5;
  padding: 8px 16px;
  border-radius: 20px;
  display: inline-block;
  margin-bottom: 25px;
  font-size: 14px;
}
.status-bar.ready { background: #e6f9f0; color: #1f935c; font-weight: bold; }

.main-layout {
  display: flex;
  justify-content: center;
  gap: 40px;
  flex-wrap: wrap;
}

.video-container, .result-container {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  width: 380px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.video-box {
  width: 100%;
  height: 250px;
  border-radius: 12px;
  overflow: hidden;
  background: #000;
}

video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.emotion-badge {
  margin-top: 15px;
  padding: 8px 20px;
  border-radius: 30px;
  font-size: 20px;
  font-weight: bold;
  box-shadow: 0 4px 10px rgba(0,0,0,0.1);
}
.emotion-badge.happy { background: #fff9db; color: #f59f00; }
.emotion-badge.sad { background: #e7f5ff; color: #228be6; }
.emotion-badge.angry { background: #fff5f5; color: #fa5252; }
.emotion-badge.surprised { background: #f3f0ff; color: #7950f2; }
.emotion-badge.neutral { background: #f1f3f5; color: #868e96; }

.meme-preview-box {
  width: 260px;
  height: 260px;
  border: 3px dashed #dee2e6;
  border-radius: 12px;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f8f9fa;
  margin-bottom: 20px;
  overflow: hidden;
}

.meme-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.placeholder-text {
  color: #adb5bd;
  font-size: 14px;
  line-height: 1.6;
}

.generate-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: bold;
  background: #e9ecef;
  color: #adb5bd;
  cursor: not-allowed;
  transition: all 0.2s;
}

.generate-btn.active {
  background: #339af0;
  color: white;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(51, 154, 240, 0.3);
}
.generate-btn.active:hover { background: #228be6; }

.tip { font-size: 12px; color: #868e96; margin-top: 10px; }
</style>