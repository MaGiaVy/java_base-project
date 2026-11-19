/**
 * =========================================================================
 * Web Dashboard MQTT Client & Industrial Hardware Station
 * Trường Đại Học Sài Gòn (SGU) - GV: Trần Minh Nhật
 * Giám Sát Cảm Biến & Điều Khiển Thiết Bị ESP32 / Mosquitto qua WebSockets
 * =========================================================================
 */

// --- Cấu hình Mặc Định & Topics ---
const TOPICS = {
  TELEMETRY: 'esp32/sensor/dht22/data',
  LEGACY_TELEMETRY: 'sensor/data',
  SENSOR_TEMP: 'sensor/temp',
  SENSOR_HUM: 'sensor/humidity',
  SENSOR_ALL: 'sensor/#',
  LED_CMD: 'esp32/led/command',
  STATUS: 'esp32/status',
  CHAT_ROOM: 'chat/room',
  CHAT_ALL: 'chat/#'
};

const PROFILES = {
  local: {
    url: 'ws://localhost:9001',
    username: 'user1',
    password: '123'
  },
  public: {
    url: 'wss://broker.emqx.io:8084/mqtt',
    username: '',
    password: ''
  }
};

let mqttClient = null;
let currentTemp = 28.6;
let currentHum = 62.4;
let isLedOn = false;
let isAutoLedMode = true;
let autoLedTriggered = false;
let realTimeChart = null;
let isAudioEnabled = true;
let audioCtx = null;

// Lưu trữ lịch sử điểm đo cho Sparklines
const sparklineData = {
  temp: [26, 27, 28, 28.5, 29, 28.6],
  hum: [60, 61, 62, 63, 62.5, 62.4]
};

const chartData = {
  labels: [],
  datasets: [
    {
      label: 'Nhiệt Độ (°C)',
      borderColor: '#f97316',
      backgroundColor: 'rgba(249, 115, 22, 0.1)',
      borderWidth: 2,
      tension: 0.35,
      fill: true,
      data: []
    },
    {
      label: 'Độ Ẩm (%)',
      borderColor: '#06b6d4',
      backgroundColor: 'rgba(6, 182, 212, 0.1)',
      borderWidth: 2,
      tension: 0.35,
      fill: true,
      data: []
    }
  ]
};

// --- Khởi tạo Chart.js an toàn ---
function initChart() {
  if (typeof Chart === 'undefined') {
    console.warn('[Chart] Thư viện Chart.js chưa sẵn sàng.');
    return;
  }
  const canvas = document.getElementById('tempChart');
  if (!canvas) return;

  try {
    const ctx = canvas.getContext('2d');
    realTimeChart = new Chart(ctx, {
      type: 'line',
      data: chartData,
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: { color: '#94a3b8' }
          },
          y: {
            min: 0,
            max: 100,
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: { color: '#94a3b8' }
          }
        },
        plugins: {
          legend: { display: false }
        }
      }
    });
  } catch (err) {
    console.warn('[Chart] Lỗi khởi tạo Chart:', err);
  }
}

// --- Hàm Vẽ Đồng Hồ Đo Kim (Analog Canvas Gauge Meter) ---
function drawGauge(canvasId, value, min, max, unit, mainColor, alertThreshold = null) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const width = canvas.width;
  const height = canvas.height;
  const centerX = width / 2;
  const centerY = height / 2 + 10;
  const radius = width * 0.38;

  ctx.clearRect(0, 0, width, height);

  const startAngle = 0.75 * Math.PI;
  const endAngle = 2.25 * Math.PI;
  const totalAngle = endAngle - startAngle;

  // 1. Vòng cung nền (Track)
  ctx.beginPath();
  ctx.arc(centerX, centerY, radius, startAngle, endAngle, false);
  ctx.lineWidth = 14;
  ctx.strokeStyle = '#1e293b';
  ctx.lineCap = 'round';
  ctx.stroke();

  // 2. Tỷ lệ giá trị
  const clampedVal = Math.min(Math.max(value, min), max);
  const ratio = (clampedVal - min) / (max - min);
  const valAngle = startAngle + ratio * totalAngle;

  // Đổi màu nếu vượt ngưỡng
  let activeColor = mainColor;
  if (alertThreshold !== null && value > alertThreshold) {
    activeColor = '#ef4444';
  }

  // 3. Vòng cung tiến độ (Progress Arc)
  if (ratio > 0) {
    ctx.beginPath();
    ctx.arc(centerX, centerY, radius, startAngle, valAngle, false);
    ctx.lineWidth = 14;
    ctx.strokeStyle = activeColor;
    ctx.lineCap = 'round';
    ctx.stroke();
  }

  // 4. Vạch chia số (Tick marks)
  for (let i = 0; i <= 10; i++) {
    const tickRatio = i / 10;
    const tickAngle = startAngle + tickRatio * totalAngle;
    const innerR = radius - 14;
    const outerR = radius - 8;
    const x1 = centerX + innerR * Math.cos(tickAngle);
    const y1 = centerY + innerR * Math.sin(tickAngle);
    const x2 = centerX + outerR * Math.cos(tickAngle);
    const y2 = centerY + outerR * Math.sin(tickAngle);

    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.lineWidth = i % 5 === 0 ? 2 : 1;
    ctx.strokeStyle = i % 5 === 0 ? '#94a3b8' : '#475569';
    ctx.stroke();
  }

  // 5. Kim chỉ (Needle)
  const needleLen = radius - 6;
  const needleX = centerX + needleLen * Math.cos(valAngle);
  const needleY = centerY + needleLen * Math.sin(valAngle);

  ctx.beginPath();
  ctx.moveTo(centerX, centerY);
  ctx.lineTo(needleX, needleY);
  ctx.lineWidth = 3;
  ctx.strokeStyle = '#f8fafc';
  ctx.stroke();

  // Tâm kim
  ctx.beginPath();
  ctx.arc(centerX, centerY, 6, 0, 2 * Math.PI);
  ctx.fillStyle = activeColor;
  ctx.fill();
  ctx.lineWidth = 2;
  ctx.strokeStyle = '#f8fafc';
  ctx.stroke();
}

// --- Hàm Vẽ Mini Sparkline (Chuẩn Slide 2 Wokwi) ---
function drawSparkline(canvasId, dataList, strokeColor, minVal, maxVal) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;

  ctx.clearRect(0, 0, w, h);
  if (dataList.length < 2) return;

  const stepX = w / (dataList.length - 1);
  ctx.beginPath();
  dataList.forEach((val, idx) => {
    const normY = (val - minVal) / (maxVal - minVal);
    const clampedY = Math.min(Math.max(normY, 0), 1);
    const y = h - clampedY * (h - 8) - 4;
    const x = idx * stepX;
    if (idx === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });

  ctx.lineWidth = 2;
  ctx.strokeStyle = strokeColor;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.stroke();

  // Điểm cuối cùng
  const lastVal = dataList[dataList.length - 1];
  const lastNormY = (lastVal - minVal) / (maxVal - minVal);
  const lastY = h - Math.min(Math.max(lastNormY, 0), 1) * (h - 8) - 4;
  ctx.beginPath();
  ctx.arc(w - 2, lastY, 3, 0, 2 * Math.PI);
  ctx.fillStyle = strokeColor;
  ctx.fill();
}

// --- Cảnh Báo Âm Thanh Web Audio Synthesizer ---
function triggerWarningSound() {
  if (!isAudioEnabled) return;
  try {
    if (!audioCtx) {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (audioCtx.state === 'suspended') {
      audioCtx.resume();
    }
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(880, audioCtx.currentTime); // 880Hz (A5)
    osc.frequency.setValueAtTime(659.25, audioCtx.currentTime + 0.1); // 659Hz (E5)
    gain.gain.setValueAtTime(0.1, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.25);
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + 0.25);
  } catch (e) {
    console.warn('[Audio] Không thể phát âm thanh:', e);
  }
}

// --- Cập nhật Giao Diện Trạng Thái Kết Nối ---
function updateConnectionUI(connected, text = null) {
  const badge = document.getElementById('connectionStatus');
  const btnConnect = document.getElementById('btnConnect');
  const btnDisconnect = document.getElementById('btnDisconnect');
  const ledWifi = document.getElementById('ledWifiStatus');
  const ledMqtt = document.getElementById('ledMqttStatus');
  const meter1883 = document.getElementById('meter1883');
  const meter9001 = document.getElementById('meter9001');

  if (connected) {
    badge.className = 'status-badge connected';
    badge.querySelector('.status-text').textContent = text || 'Đã kết nối (Online)';
    btnConnect.disabled = true;
    btnDisconnect.disabled = false;

    if (ledWifi) ledWifi.className = 'led-light led-blue active';
    if (ledMqtt) ledMqtt.className = 'led-light led-green active';
    if (meter1883) meter1883.className = 'port-led active';
    if (meter9001) meter9001.className = 'port-led active';
  } else {
    badge.className = 'status-badge disconnected';
    badge.querySelector('.status-text').textContent = text || 'Chưa kết nối (Offline)';
    btnConnect.disabled = false;
    btnDisconnect.disabled = true;

    if (ledMqtt) ledMqtt.className = 'led-light led-green';
    if (meter9001) meter9001.className = 'port-led';
  }
}

// --- Cập nhật Giao Diện Đèn LED ---
function updateLedUI(state) {
  isLedOn = (state === 'ON' || state === true);
  const toggle = document.getElementById('ledToggle');
  const statusText = document.getElementById('ledStatusText');
  const physicalLed = document.getElementById('physicalLedBulb');
  const ledGpio4 = document.getElementById('ledGpio4Status');

  if (toggle) toggle.checked = isLedOn;

  if (isLedOn) {
    if (statusText) {
      statusText.textContent = 'BẬT (ON)';
      statusText.className = 'badge-on';
    }
    if (physicalLed) {
      physicalLed.className = 'physical-led on';
    }
    if (ledGpio4) {
      ledGpio4.className = 'led-light led-d4 active';
    }
  } else {
    if (statusText) {
      statusText.textContent = 'TẮT (OFF)';
      statusText.className = 'badge-off';
    }
    if (physicalLed) {
      physicalLed.className = 'physical-led off';
    }
    if (ledGpio4) {
      ledGpio4.className = 'led-light led-d4';
    }
  }
}

// --- Cập nhật số đo cảm biến toàn diện ---
function updateTelemetry(temp, hum) {
  let updated = false;

  if (temp !== undefined && !isNaN(temp)) {
    currentTemp = parseFloat(temp);
    const tempStr = currentTemp.toFixed(1);

    // 1. Cập nhật thẻ Analog Gauge
    const elTemp = document.getElementById('tempValue');
    if (elTemp) elTemp.textContent = tempStr;
    drawGauge('tempGauge', currentTemp, 0, 100, '°C', '#f97316', 35.0);

    // 2. Cập nhật Trạm Phần Cứng
    const elDhtTemp = document.getElementById('dhtTempDisplay');
    if (elDhtTemp) elDhtTemp.textContent = `${tempStr} °C`;

    // 3. Cập nhật Widget Wokwi Display
    const elWokwiTemp = document.getElementById('wokwiTempValue');
    if (elWokwiTemp) elWokwiTemp.textContent = `${tempStr} °C`;

    // 4. Sparkline & Alert
    sparklineData.temp.push(currentTemp);
    if (sparklineData.temp.length > 12) sparklineData.temp.shift();
    drawSparkline('sparklineTemp', sparklineData.temp, '#f97316', 15, 45);

    const alertBadge = document.getElementById('tempBadgeAlert');
    if (alertBadge) {
      if (currentTemp > 35.0) {
        alertBadge.textContent = '🔥 CẢNH BÁO QUÁ NHIỆT';
        alertBadge.className = 'badge-alert';
        triggerWarningSound();
        if (isAutoLedMode && !isLedOn) {
          autoLedTriggered = true;
          sendLedCommand('ON');
          appendLog('system/alert', `[Tự động] Nhiệt độ ${tempStr}°C > 35°C -> Bật đèn LED cảnh báo!`);
        }
      } else {
        alertBadge.textContent = 'Bình thường';
        alertBadge.className = 'badge-normal';
        if (isAutoLedMode && autoLedTriggered) {
          autoLedTriggered = false;
          if (isLedOn) sendLedCommand('OFF');
          appendLog('system/normal', `[Tự động] Nhiệt độ ${tempStr}°C hạ nhiệt -> Tắt đèn LED!`);
        }
      }
    }
    updated = true;
  }

  if (hum !== undefined && !isNaN(hum)) {
    currentHum = parseFloat(hum);
    const humStr = currentHum.toFixed(1);

    // 1. Cập nhật thẻ Analog Gauge
    const elHum = document.getElementById('humValue');
    if (elHum) elHum.textContent = humStr;
    drawGauge('humGauge', currentHum, 0, 100, '%', '#06b6d4');

    // 2. Cập nhật Trạm Phần Cứng
    const elDhtHum = document.getElementById('dhtHumDisplay');
    if (elDhtHum) elDhtHum.textContent = `${humStr} %`;

    // 3. Cập nhật Widget Wokwi Display
    const elWokwiHum = document.getElementById('wokwiHumValue');
    if (elWokwiHum) elWokwiHum.textContent = `${humStr} %`;

    // 4. Sparkline
    sparklineData.hum.push(currentHum);
    if (sparklineData.hum.length > 12) sparklineData.hum.shift();
    drawSparkline('sparklineHum', sparklineData.hum, '#06b6d4', 20, 95);

    updated = true;
  }

  // Cập nhật biểu đồ chính nếu có dữ liệu
  if (updated && realTimeChart) {
    const timeLabel = new Date().toTimeString().split(' ')[0];
    chartData.labels.push(timeLabel);
    chartData.datasets[0].data.push(currentTemp);
    chartData.datasets[1].data.push(currentHum);

    if (chartData.labels.length > 18) {
      chartData.labels.shift();
      chartData.datasets[0].data.shift();
      chartData.datasets[1].data.shift();
    }
    realTimeChart.update();
  }
}

// --- Ghi Nhật Ký Thông Điệp ---
function appendLog(topic, message) {
  const tableBody = document.getElementById('logTableBody');
  if (!tableBody) return;
  const emptyRow = tableBody.querySelector('.empty-row');
  if (emptyRow) emptyRow.remove();

  const now = new Date();
  const timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');

  const row = document.createElement('tr');
  row.innerHTML = `
    <td>${timeStr}</td>
    <td><code>${escapeHtml(topic)}</code></td>
    <td>${escapeHtml(message)}</td>
  `;

  tableBody.insertBefore(row, tableBody.firstChild);
  if (tableBody.children.length > 50) {
    tableBody.removeChild(tableBody.lastChild);
  }
}

// --- Ghi Tin Nhắn Vào Khung Chat MQTT ---
function appendChatMessage(sender, text, isSelf = false) {
  const chatList = document.getElementById('chatMessageList');
  if (!chatList) return;

  const now = new Date();
  const timeStr = now.toTimeString().split(' ')[0];
  const item = document.createElement('div');
  item.className = `chat-item ${isSelf ? 'sent-msg' : 'received-msg'}`;
  item.innerHTML = `
    <span class="time">[${timeStr}]</span>
    <span class="sender">${escapeHtml(sender)}:</span>
    <span class="text">${escapeHtml(text)}</span>
  `;

  chatList.appendChild(item);
  chatList.scrollTop = chatList.scrollHeight;
}

function escapeHtml(str) {
  if (typeof str !== 'string') str = String(str);
  return str.replace(/[&<>"']/g, tag => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[tag] || tag));
}

// --- Quản Lý Kết Nối MQTT ---
function connectMQTT() {
  if (typeof mqtt === 'undefined') {
    alert('Thư viện mqtt.js chưa tải được. Vui lòng kiểm tra lại kết nối mạng hoặc file web/lib/mqtt.min.js');
    return;
  }

  const url = document.getElementById('brokerUrl').value.trim();
  const username = document.getElementById('brokerUsername').value.trim();
  const password = document.getElementById('brokerPassword').value.trim();

  updateConnectionUI(false, 'Đang kết nối...');
  appendLog('system/status', `Bắt đầu kết nối tới ${url}...`);

  const clientId = 'WebDashboard_SGU_' + Math.random().toString(16).substring(2, 8);
  const options = {
    clientId: clientId,
    clean: true,
    connectTimeout: 5000,
    reconnectPeriod: 3000
  };

  if (username) options.username = username;
  if (password) options.password = password;

  try {
    if (mqttClient) {
      mqttClient.end(true);
    }
    mqttClient = mqtt.connect(url, options);

    mqttClient.on('connect', () => {
      console.log('[MQTT] Đã kết nối thành công!');
      updateConnectionUI(true, 'Đã kết nối (Online)');
      appendLog('system/status', `Kết nối thành công! ClientID: ${clientId}`);

      // Trên Public EMQX chỉ đăng ký 3 topic của dự án để đồng bộ sạch với Java Client & Wokwi
      const isPublicBroker = url.includes('emqx.io');
      const subscribeTopics = isPublicBroker
        ? [TOPICS.TELEMETRY, TOPICS.LED_CMD, TOPICS.STATUS]
        : [
            TOPICS.TELEMETRY,
            TOPICS.LEGACY_TELEMETRY,
            TOPICS.SENSOR_ALL,
            TOPICS.LED_CMD,
            TOPICS.STATUS,
            TOPICS.CHAT_ALL,
            'test/#'
          ];

      subscribeTopics.forEach(top => {
        mqttClient.subscribe(top, (err) => {
          if (!err) appendLog('system/subscribe', `Đã đăng ký (Subscribed): ${top}`);
        });
      });
    });

    mqttClient.on('message', (topic, payload) => {
      const msgStr = payload.toString();
      appendLog(topic, msgStr);

      // 1. Phân loại tin nhắn Chat
      if (topic.startsWith('chat/') || topic.startsWith('test/')) {
        const sender = topic.includes('room') ? 'Phòng Chat' : topic;
        appendChatMessage(sender, msgStr, false);
      }

      // 2. Xử lý dữ liệu cảm biến DHT22
      if (topic === TOPICS.TELEMETRY || topic === TOPICS.LEGACY_TELEMETRY || topic.includes('sensor')) {
        try {
          const data = JSON.parse(msgStr);
          const t = data.temp !== undefined ? data.temp : data.temperature;
          const h = data.hum !== undefined ? data.hum : data.humidity;
          if (t !== undefined || h !== undefined) {
            updateTelemetry(t, h);
            return;
          }
        } catch (e) {
          // Payload là số thô (ví dụ: topic "sensor/temp" payload "30")
          const num = parseFloat(msgStr);
          if (!isNaN(num)) {
            if (topic.includes('temp')) updateTelemetry(num, undefined);
            else if (topic.includes('hum')) updateTelemetry(undefined, num);
          }
        }
      }

      // 3. Lệnh điều khiển LED
      if (topic === TOPICS.LED_CMD) {
        const cleanCmd = msgStr.trim().toUpperCase();
        if (cleanCmd === 'ON' || cleanCmd === '1') updateLedUI('ON');
        else if (cleanCmd === 'OFF' || cleanCmd === '0') updateLedUI('OFF');
      }
    });

    mqttClient.on('error', (err) => {
      console.error('[MQTT] Lỗi kết nối:', err);
      updateConnectionUI(false, 'Lỗi kết nối');
      appendLog('system/error', `Lỗi: ${err.message || err}`);
    });

    mqttClient.on('close', () => {
      updateConnectionUI(false, 'Chưa kết nối (Offline)');
    });

  } catch (err) {
    console.error('[MQTT] Exception:', err);
    updateConnectionUI(false, 'Lỗi ngoại lệ');
    appendLog('system/error', `Exception: ${err.message}`);
  }
}

function disconnectMQTT() {
  if (mqttClient) {
    mqttClient.end(false, () => {
      updateConnectionUI(false, 'Đã ngắt kết nối');
      appendLog('system/status', 'Người dùng đã chủ động ngắt kết nối.');
    });
  }
}

// --- Gửi Lệnh Bật/Tắt Đèn LED ---
function sendLedCommand(cmd) {
  if (!mqttClient || !mqttClient.connected) {
    // Cho phép đổi trạng thái cục bộ để test giao diện
    updateLedUI(cmd);
    appendLog(TOPICS.LED_CMD, `${cmd} (Mô phỏng cục bộ - chưa kết nối broker)`);
    return;
  }
  mqttClient.publish(TOPICS.LED_CMD, cmd, { qos: 1 }, (err) => {
    if (!err) {
      updateLedUI(cmd);
      appendLog(TOPICS.LED_CMD, `Đã gửi lệnh điều khiển: ${cmd}`);
    } else {
      appendLog('system/error', `Gửi lệnh LED thất bại: ${err.message}`);
    }
  });
}

// --- Gửi Tin Nhắn Chat MQTT ---
function sendChatMessage() {
  const inputEl = document.getElementById('chatTextInput');
  const topicEl = document.getElementById('chatTopicInput');
  if (!inputEl || !topicEl) return;

  const msg = inputEl.value.trim();
  const topic = topicEl.value.trim() || 'chat/room';
  if (!msg) return;

  if (mqttClient && mqttClient.connected) {
    mqttClient.publish(topic, msg, { qos: 0 }, (err) => {
      if (!err) {
        appendChatMessage('Tôi (Web)', msg, true);
        appendLog(topic, `[Chat Web] ${msg}`);
        inputEl.value = '';
      }
    });
  } else {
    // Mô phỏng chat cục bộ
    appendChatMessage('Tôi (Cục bộ)', msg, true);
    appendLog(topic, `[Chat Local] ${msg}`);
    inputEl.value = '';
  }
}

// --- Xuất Báo Cáo Ra File CSV ---
function exportLogsToCsv() {
  const table = document.getElementById('logTable');
  if (!table) return;

  let csvContent = 'data:text/csv;charset=utf-8,Thoi Gian,Topic,Noi Dung Payload\r\n';
  const rows = table.querySelectorAll('tbody tr');
  let hasData = false;

  rows.forEach(r => {
    if (r.classList.contains('empty-row')) return;
    const cols = r.querySelectorAll('td');
    if (cols.length >= 3) {
      const time = `"${cols[0].textContent.trim()}"`;
      const topic = `"${cols[1].textContent.trim().replace(/"/g, '""')}"`;
      const payload = `"${cols[2].textContent.trim().replace(/"/g, '""')}"`;
      csvContent += `${time},${topic},${payload}\r\n`;
      hasData = true;
    }
  });

  if (!hasData) {
    alert('Chưa có thông điệp nào để xuất báo cáo CSV!');
    return;
  }

  const encodedUri = encodeURI(csvContent);
  const link = document.createElement('a');
  link.setAttribute('href', encodedUri);
  link.setAttribute('download', `IoT_Logs_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

// --- Khởi Tạo Ứng Dụng ---
function initApp() {
  // 1. Đồng hồ thời gian thực
  setInterval(() => {
    const elClock = document.getElementById('liveClock');
    if (elClock) {
      elClock.textContent = new Date().toTimeString().split(' ')[0];
    }
  }, 1000);

  // 2. Khởi tạo Chart & Gauges ban đầu
  initChart();
  updateTelemetry(28.6, 62.4);

  // 3. Gắn sự kiện kết nối
  const btnConn = document.getElementById('btnConnect');
  const btnDisconn = document.getElementById('btnDisconnect');
  if (btnConn) btnConn.addEventListener('click', connectMQTT);
  if (btnDisconn) btnDisconn.addEventListener('click', disconnectMQTT);

  // 4. Điều khiển LED
  const toggle = document.getElementById('ledToggle');
  if (toggle) {
    toggle.addEventListener('change', (e) => {
      sendLedCommand(e.target.checked ? 'ON' : 'OFF');
    });
  }
  const btnOn = document.getElementById('btnTurnOn');
  const btnOff = document.getElementById('btnTurnOff');
  if (btnOn) btnOn.addEventListener('click', () => sendLedCommand('ON'));
  if (btnOff) btnOff.addEventListener('click', () => sendLedCommand('OFF'));

  const chkAuto = document.getElementById('chkAutoLed');
  if (chkAuto) {
    chkAuto.addEventListener('change', (e) => {
      isAutoLedMode = e.target.checked;
      appendLog('system/config', `Chế độ tự động bật đèn theo nhiệt độ: ${isAutoLedMode ? 'BẬT' : 'TẮT'}`);
    });
  }

  // 5. Nút chuyển cấu hình Profile
  const btnLocal = document.getElementById('btnLocalProfile');
  const btnPublic = document.getElementById('btnPublicProfile');

  if (btnLocal && btnPublic) {
    btnLocal.addEventListener('click', () => {
      btnLocal.classList.add('active');
      btnPublic.classList.remove('active');
      document.getElementById('brokerUrl').value = PROFILES.local.url;
      document.getElementById('brokerUsername').value = PROFILES.local.username;
      document.getElementById('brokerPassword').value = PROFILES.local.password;
      connectMQTT();
    });

    btnPublic.addEventListener('click', () => {
      btnPublic.classList.add('active');
      btnLocal.classList.remove('active');
      document.getElementById('brokerUrl').value = PROFILES.public.url;
      document.getElementById('brokerUsername').value = PROFILES.public.username;
      document.getElementById('brokerPassword').value = PROFILES.public.password;
      connectMQTT();
    });
  }

  // 6. Thanh công cụ Test nhanh (Simulation Presets)
  const publishSimPreset = (temp, hum) => {
    const payload = JSON.stringify({ temperature: temp, humidity: hum, temp: temp, hum: hum });
    if (mqttClient && mqttClient.connected) mqttClient.publish(TOPICS.TELEMETRY, payload);
    updateTelemetry(temp, hum);
    appendLog(TOPICS.TELEMETRY, payload);
  };

  const btnSimDefault = document.getElementById('simSlideDefault');
  if (btnSimDefault) btnSimDefault.addEventListener('click', () => publishSimPreset(28.6, 62.4));

  const btnSimOverheat = document.getElementById('simOverheat');
  if (btnSimOverheat) btnSimOverheat.addEventListener('click', () => publishSimPreset(39.2, 78.0));

  const btnSimCool = document.getElementById('simCool');
  if (btnSimCool) btnSimCool.addEventListener('click', () => publishSimPreset(20.5, 50.0));

  const btnSimLed = document.getElementById('simToggleLed');
  if (btnSimLed) {
    btnSimLed.addEventListener('click', () => {
      sendLedCommand(isLedOn ? 'OFF' : 'ON');
    });
  }

  // 7. MQTT Chat & Command Console
  const btnSendChat = document.getElementById('btnSendChat');
  const chatInput = document.getElementById('chatTextInput');
  if (btnSendChat) btnSendChat.addEventListener('click', sendChatMessage);
  if (chatInput) {
    chatInput.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') sendChatMessage();
    });
  }

  // 8. Bật/Tắt âm thanh cảnh báo
  const btnAudio = document.getElementById('btnAudioToggle');
  if (btnAudio) {
    btnAudio.addEventListener('click', () => {
      isAudioEnabled = !isAudioEnabled;
      document.getElementById('audioIcon').textContent = isAudioEnabled ? '🔊' : '🔇';
      document.getElementById('audioText').textContent = isAudioEnabled ? 'Âm thanh: BẬT' : 'Âm thanh: TẮT';
    });
  }

  // 9. Nút xóa nhật ký & Xuất CSV
  const btnClear = document.getElementById('btnClearLogs');
  if (btnClear) {
    btnClear.addEventListener('click', () => {
      const tb = document.getElementById('logTableBody');
      if (tb) tb.innerHTML = '<tr class="empty-row"><td colspan="3">Chưa có thông điệp nào nhận được...</td></tr>';
    });
  }

  const btnExport = document.getElementById('btnExportCsv');
  if (btnExport) btnExport.addEventListener('click', exportLogsToCsv);
}

// Khởi chạy khi tải xong trang
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}
window.addEventListener('load', () => {
  drawGauge('tempGauge', currentTemp || 28.6, 0, 100, '°C', '#f97316', 35.0);
  drawGauge('humGauge', currentHum || 62.4, 0, 100, '%', '#06b6d4');
  drawSparkline('sparklineTemp', sparklineData.temp, '#f97316', 15, 45);
  drawSparkline('sparklineHum', sparklineData.hum, '#06b6d4', 20, 95);
});
