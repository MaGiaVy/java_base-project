/**
 * =========================================================================
 * Web Dashboard MQTT Client & Real-time Visualization
 * Điều khiển & Giám sát ESP32 Wokwi qua WebSockets
 * =========================================================================
 */

// --- Cấu hình Mặc Định & Topics ---
const TOPICS = {
  TELEMETRY: 'esp32/sensor/dht22/data',
  LEGACY_TELEMETRY: 'sensor/data',
  LED_CMD: 'esp32/led/command',
  STATUS: 'esp32/status'
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
let currentTemp = 0;
let currentHum = 0;
let isLedOn = false;

// --- Khởi tạo Chart.js ---
const ctxChart = document.getElementById('tempChart').getContext('2d');
const chartData = {
  labels: [],
  datasets: [
    {
      label: 'Nhiệt Độ (°C)',
      borderColor: '#f97316',
      backgroundColor: 'rgba(249, 115, 22, 0.1)',
      data: [],
      tension: 0.3,
      fill: true
    },
    {
      label: 'Độ Ẩm (%)',
      borderColor: '#06b6d4',
      backgroundColor: 'rgba(6, 182, 212, 0.1)',
      data: [],
      tension: 0.3,
      fill: true
    }
  ]
};

const realTimeChart = new Chart(ctxChart, {
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

// --- Hàm Vẽ Đồng Hồ Đo (Canvas Gauge Meter) ---
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

  // 1. Vẽ vòng cung nền (Track)
  ctx.beginPath();
  ctx.arc(centerX, centerY, radius, startAngle, endAngle, false);
  ctx.lineWidth = 16;
  ctx.strokeStyle = '#243049';
  ctx.lineCap = 'round';
  ctx.stroke();

  // 2. Tính góc tỷ lệ theo giá trị
  const clampedVal = Math.min(Math.max(value, min), max);
  const ratio = (clampedVal - min) / (max - min);
  const valAngle = startAngle + ratio * totalAngle;

  // Xác định màu sắc (Đổi sang đỏ rực nếu vượt ngưỡng cảnh báo)
  let activeColor = mainColor;
  if (alertThreshold !== null && value > alertThreshold) {
    activeColor = '#ef4444';
  }

  // 3. Vẽ vòng cung tiến độ (Progress Arc)
  if (ratio > 0) {
    ctx.beginPath();
    ctx.arc(centerX, centerY, radius, startAngle, valAngle, false);
    ctx.lineWidth = 16;
    ctx.strokeStyle = activeColor;
    ctx.lineCap = 'round';
    ctx.stroke();
  }

  // 4. Vẽ kim chỉ (Needle)
  const needleLen = radius - 8;
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
  ctx.fillStyle = '#f8fafc';
  ctx.fill();
}

// --- Hàm Cập Nhật Trạng Thái Giao Diện ---
function updateConnectionUI(connected, text = null) {
  const badge = document.getElementById('connectionStatus');
  const btnConnect = document.getElementById('btnConnect');
  const btnDisconnect = document.getElementById('btnDisconnect');

  if (connected) {
    badge.className = 'status-badge connected';
    badge.querySelector('.status-text').textContent = text || 'Đã kết nối (Online)';
    btnConnect.disabled = true;
    btnDisconnect.disabled = false;
  } else {
    badge.className = 'status-badge disconnected';
    badge.querySelector('.status-text').textContent = text || 'Ngắt kết nối (Offline)';
    btnConnect.disabled = false;
    btnDisconnect.disabled = true;
  }
}

function updateLedUI(state) {
  isLedOn = (state === true || state === 'ON' || state === '1');
  const bulb = document.getElementById('ledVisual');
  const label = document.getElementById('ledStatusText');
  const toggle = document.getElementById('ledToggle');

  if (isLedOn) {
    bulb.className = 'led-bulb on';
    label.textContent = 'BẬT (ON)';
    label.style.color = '#4ade80';
    toggle.checked = true;
  } else {
    bulb.className = 'led-bulb off';
    label.textContent = 'TẮT (OFF)';
    label.style.color = '#94a3b8';
    toggle.checked = false;
  }
}

function appendLog(topic, message) {
  const tableBody = document.getElementById('logTableBody');
  const emptyRow = tableBody.querySelector('.empty-row');
  if (emptyRow) emptyRow.remove();

  const now = new Date();
  const timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');

  const row = document.createElement('tr');
  row.innerHTML = `
    <td><code>${timeStr}</code></td>
    <td><span style="color:#38bdf8; font-weight:600;">${topic}</span></td>
    <td><code>${escapeHtml(message)}</code></td>
  `;
  tableBody.insertBefore(row, tableBody.firstChild);

  // Giữ tối đa 50 dòng log gần nhất
  if (tableBody.children.length > 50) {
    tableBody.removeChild(tableBody.lastChild);
  }
}

function escapeHtml(str) {
  return str.replace(/[&<>'"]/g, tag => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[tag] || tag));
}

// --- Quản Lý Kết Nối MQTT ---
function connectMQTT() {
  const url = document.getElementById('brokerUrl').value.trim();
  const username = document.getElementById('brokerUsername').value.trim();
  const password = document.getElementById('brokerPassword').value.trim();

  updateConnectionUI(false, 'Đang kết nối...');

  if (mqttClient) {
    try { mqttClient.end(true); } catch (e) {}
  }

  const clientId = 'WebDashboard_' + Math.random().toString(16).substring(2, 10);
  const options = {
    clientId: clientId,
    clean: true,
    connectTimeout: 5000,
    reconnectPeriod: 3000
  };

  if (username) options.username = username;
  if (password) options.password = password;

  try {
    mqttClient = mqtt.connect(url, options);

    mqttClient.on('connect', () => {
      console.log('[MQTT] Kết nối thành công tới:', url);
      updateConnectionUI(true, 'Đã kết nối Broker');

      // Đăng ký nhận dữ liệu cảm biến (topic chính) và lệnh điều khiển
      mqttClient.subscribe(TOPICS.TELEMETRY, { qos: 0 });
      mqttClient.subscribe(TOPICS.LED_CMD, { qos: 0 });
      mqttClient.subscribe(TOPICS.STATUS, { qos: 0 });

      appendLog('[HỆ THỐNG]', `Đã kết nối Broker ${url} & subscribe topics`);
    });

    mqttClient.on('message', (topic, payload) => {
      const msgStr = payload.toString();
      appendLog(topic, msgStr);

      if (topic === TOPICS.TELEMETRY || topic === TOPICS.LEGACY_TELEMETRY) {
        try {
          const data = JSON.parse(msgStr);
          if (data.temperature !== undefined) {
            currentTemp = parseFloat(data.temperature);
            document.getElementById('tempValue').textContent = currentTemp.toFixed(1);
            drawGauge('tempGauge', currentTemp, 0, 100, '°C', '#f97316', 35.0);
          }
          if (data.humidity !== undefined) {
            currentHum = parseFloat(data.humidity);
            document.getElementById('humValue').textContent = currentHum.toFixed(1);
            drawGauge('humGauge', currentHum, 0, 100, '%', '#06b6d4');
          }

          // Cập nhật biểu đồ thời gian thực
          const timeLabel = new Date().toTimeString().split(' ')[0];
          chartData.labels.push(timeLabel);
          chartData.datasets[0].data.push(currentTemp);
          chartData.datasets[1].data.push(currentHum);

          if (chartData.labels.length > 15) {
            chartData.labels.shift();
            chartData.datasets[0].data.shift();
            chartData.datasets[1].data.shift();
          }
          realTimeChart.update();
        } catch (e) {
          console.warn('[MQTT] Lỗi parse JSON payload:', msgStr);
        }
      } else if (topic === TOPICS.LED_CMD) {
        updateLedUI(msgStr);
      }
    });

    mqttClient.on('close', () => {
      updateConnectionUI(false, 'Mất kết nối');
    });

    mqttClient.on('error', (err) => {
      console.error('[MQTT Error]:', err);
      updateConnectionUI(false, 'Lỗi kết nối');
      appendLog('[LỖI]', `Không thể kết nối WebSocket: ${err.message || err}`);
    });

  } catch (err) {
    console.error('[Exception]:', err);
    updateConnectionUI(false, 'Lỗi khởi tạo');
  }
}

function disconnectMQTT() {
  if (mqttClient) {
    mqttClient.end();
    mqttClient = null;
    updateConnectionUI(false, 'Đã ngắt kết nối');
    appendLog('[HỆ THỐNG]', 'Người dùng đã ngắt kết nối Broker.');
  }
}

function sendLedCommand(stateStr) {
  if (!mqttClient || !mqttClient.connected) {
    alert('Vui lòng kết nối tới MQTT Broker trước khi điều khiển!');
    return;
  }
  mqttClient.publish(TOPICS.LED_CMD, stateStr, { qos: 0, retain: false });
  updateLedUI(stateStr);
  appendLog(`[GỬI] ${TOPICS.LED_CMD}`, stateStr);
}

// --- Thiết lập sự kiện giao diện ---
document.addEventListener('DOMContentLoaded', () => {
  // Vẽ gauge ban đầu ở mức 0
  drawGauge('tempGauge', 0, 0, 100, '°C', '#f97316', 35.0);
  drawGauge('humGauge', 0, 0, 100, '%', '#06b6d4');
  updateLedUI(false);

  // Nút Kết nối / Ngắt kết nối
  document.getElementById('btnConnect').addEventListener('click', connectMQTT);
  document.getElementById('btnDisconnect').addEventListener('click', disconnectMQTT);

  // Điều khiển đèn LED
  document.getElementById('ledToggle').addEventListener('change', (e) => {
    sendLedCommand(e.target.checked ? 'ON' : 'OFF');
  });
  document.getElementById('btnTurnOn').addEventListener('click', () => sendLedCommand('ON'));
  document.getElementById('btnTurnOff').addEventListener('click', () => sendLedCommand('OFF'));

  // Nút chuyển profile nhanh
  const btnLocal = document.getElementById('btnLocalProfile');
  const btnPublic = document.getElementById('btnPublicProfile');

  btnLocal.addEventListener('click', () => {
    btnLocal.classList.add('active');
    btnPublic.classList.remove('active');
    document.getElementById('brokerUrl').value = PROFILES.local.url;
    document.getElementById('brokerUsername').value = PROFILES.local.username;
    document.getElementById('brokerPassword').value = PROFILES.local.password;
  });

  btnPublic.addEventListener('click', () => {
    btnPublic.classList.add('active');
    btnLocal.classList.remove('active');
    document.getElementById('brokerUrl').value = PROFILES.public.url;
    document.getElementById('brokerUsername').value = PROFILES.public.username;
    document.getElementById('brokerPassword').value = PROFILES.public.password;
  });

  // Nút xóa log
  document.getElementById('btnClearLogs').addEventListener('click', () => {
    document.getElementById('logTableBody').innerHTML = `
      <tr class="empty-row"><td colspan="3">Chưa có thông điệp nào nhận được...</td></tr>
    `;
  });
});
