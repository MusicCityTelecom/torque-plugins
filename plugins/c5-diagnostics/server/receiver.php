<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

function fail(int $status, string $message): never {
    http_response_code($status);
    echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_SLASHES);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    fail(405, 'method_not_allowed');
}

$secret = getenv('TORQUE_WEBHOOK_SECRET');
if ($secret === false || strlen($secret) < 24) {
    error_log('TORQUE_WEBHOOK_SECRET is missing or too short');
    fail(503, 'receiver_not_configured');
}

$timestamp = $_SERVER['HTTP_X_TORQUE_TIMESTAMP'] ?? '';
$signature = $_SERVER['HTTP_X_TORQUE_SIGNATURE'] ?? '';

if (!preg_match('/^\d{10,}$/', $timestamp)) {
    fail(401, 'bad_timestamp');
}

$now = time();
if (abs($now - (int)$timestamp) > 300) {
    fail(401, 'stale_timestamp');
}

$contentLength = isset($_SERVER['CONTENT_LENGTH']) ? (int)$_SERVER['CONTENT_LENGTH'] : 0;
if ($contentLength > 2 * 1024 * 1024) {
    fail(413, 'report_too_large');
}

$body = file_get_contents('php://input', false, null, 0, 2 * 1024 * 1024 + 1);
if ($body === false || $body === '') {
    fail(400, 'empty_body');
}
if (strlen($body) > 2 * 1024 * 1024) {
    fail(413, 'report_too_large');
}

$expected = 'sha256=' . hash_hmac('sha256', $timestamp . '.' . $body, $secret);
if (!hash_equals($expected, $signature)) {
    fail(401, 'bad_signature');
}

$data = json_decode($body, true);
if (!is_array($data) || json_last_error() !== JSON_ERROR_NONE) {
    fail(400, 'invalid_json');
}

$sessionId = (string)($data['session']['id'] ?? '');
if ($sessionId === '') {
    fail(400, 'missing_session_id');
}

$safeSessionId = preg_replace('/[^A-Za-z0-9._-]/', '_', $sessionId);
if ($safeSessionId === null || $safeSessionId === '') {
    fail(400, 'invalid_session_id');
}

$reportDir = getenv('TORQUE_REPORT_DIR');
if ($reportDir === false || $reportDir === '') {
    $reportDir = '/var/lib/torque-reports';
}

if (!is_dir($reportDir) && !mkdir($reportDir, 0750, true) && !is_dir($reportDir)) {
    error_log('Unable to create TORQUE_REPORT_DIR: ' . $reportDir);
    fail(500, 'storage_unavailable');
}

$filename = rtrim($reportDir, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR .
    gmdate('Ymd-His') . '-' . $safeSessionId . '.json';

if (file_put_contents($filename, $body . PHP_EOL, LOCK_EX) === false) {
    error_log('Unable to write report: ' . $filename);
    fail(500, 'write_failed');
}

@chmod($filename, 0640);

http_response_code(202);
echo json_encode([
    'ok' => true,
    'accepted' => true,
    'session_id' => $sessionId,
], JSON_UNESCAPED_SLASHES);
