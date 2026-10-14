<?php

declare(strict_types=1);

date_default_timezone_set('Europe/Berlin');
error_reporting(0);
ini_set('display_errors', '0');

$projectRoots = [dirname(__DIR__), dirname(__DIR__, 2)];
foreach ($projectRoots as $projectRoot) {
    $autoload = $projectRoot . '/vendor/autoload.php';
    if (is_file($autoload)) {
        require_once $autoload;
        break;
    }
}

if (class_exists(\Dotenv\Dotenv::class)) {
    foreach ($projectRoots as $projectRoot) {
        if (is_file($projectRoot . '/.env')) {
            \Dotenv\Dotenv::createImmutable($projectRoot)->safeLoad();
            break;
        }
    }
}

const TC_BDT_TABLE = 'bdt_1_8_9';
const TC_BDT_VOTES_TABLE = 'bdt_votes_1_8_9';
const TC_VERIFICATION_TABLE = 'verification_requests_1_8_9';

function tc_env(string $name, ?string $default = null): ?string
{
    $value = $_ENV[$name] ?? getenv($name);
    return $value === false || $value === null || $value === '' ? $default : (string) $value;
}

function tc_json(array $payload, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function tc_error(int $status, string $message, array $extra = []): never
{
    tc_json(array_merge(['success' => false, 'message' => $message], $extra), $status);
}

function tc_body(): string
{
    static $body;
    if ($body === null) {
        $read = file_get_contents('php://input');
        $body = is_string($read) ? $read : '';
    }
    return $body;
}

function tc_input(): array
{
    try {
        $decoded = json_decode(tc_body(), true, 32, JSON_THROW_ON_ERROR);
    } catch (Throwable) {
        tc_error(400, 'Ungültige Anfrage.');
    }
    if (!is_array($decoded)) {
        tc_error(400, 'Ungültige Anfrage.');
    }
    return $decoded;
}

function tc_require_method(string $method): void
{
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== $method) {
        header('Allow: ' . $method);
        tc_error(405, 'Methode nicht erlaubt.');
    }
}

function tc_require_signature(): void
{
    $timestamp = $_SERVER['HTTP_X_TIMESTAMP'] ?? '';
    $signature = strtolower((string) ($_SERVER['HTTP_X_SIGNATURE'] ?? ''));
    $secret = tc_env(
        'TRADECORE_1_8_9_HMAC_SECRET',
        'f#K&9@zP!q8$T6n2*sE5vGjR7wX4yH3bA^uC1dZ@LpM!oI0kS_gV-e'
    );

    if (!ctype_digit((string) $timestamp) || !preg_match('/^[a-f0-9]{64}$/', $signature)) {
        tc_error(401, 'Ungültige Signatur.');
    }
    if (abs(time() - (int) $timestamp) > 300) {
        tc_error(401, 'Abgelaufene Signatur.');
    }

    $expected = hash_hmac('sha256', tc_body() . $timestamp, (string) $secret);
    if (!hash_equals($expected, $signature)) {
        tc_error(401, 'Ungültige Signatur.');
    }
}

function tc_require_legacy_version(array $input): void
{
    $version = trim((string) ($input['minecraftVersion'] ?? '1.8.9'));
    if ($version !== '1.8.9') {
        tc_error(400, 'Dieser Endpunkt ist nur für Minecraft 1.8.9 bestimmt.');
    }
}

function tc_db(): PDO
{
    static $pdo;
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $host = tc_env('DB_HOST');
    $name = tc_env('DB_NAME');
    $user = tc_env('DB_USER');
    $pass = tc_env('DB_PASS', '');
    if ($host === null || $name === null || $user === null) {
        throw new RuntimeException('Datenbank-Konfiguration fehlt.');
    }

    $pdo = new PDO(
        "mysql:host={$host};dbname={$name};charset=utf8mb4",
        $user,
        $pass,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]
    );
    // CURDATE() und NOW() müssen für den täglichen BdT der Berliner Zeit folgen.
    // Der numerische Offset funktioniert auch ohne installierte MySQL-Zeitzonentabellen.
    $pdo->exec('SET time_zone = ' . $pdo->quote(date('P')));
    return $pdo;
}

function tc_valid_uuid(string $uuid): bool
{
    return preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i', $uuid) === 1;
}

function tc_log(string $scope, string $message): void
{
    $directory = __DIR__ . '/log';
    if (!is_dir($directory)) {
        @mkdir($directory, 0775, true);
    }
    $safeScope = preg_replace('/[^a-z0-9_-]/i', '_', $scope) ?: 'api';
    $ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
    @error_log(sprintf("[%s] [%s] %s\n", date('c'), $ip, $message), 3, $directory . '/' . $safeScope . '.log');
}

function tc_public_base_url(): string
{
    $configured = tc_env('TRADECORE_1_8_9_PUBLIC_BASE_URL');
    if ($configured !== null) {
        return rtrim($configured, '/');
    }

    $host = preg_replace('/[^a-z0-9.:-]/i', '', (string) ($_SERVER['HTTP_HOST'] ?? ''));
    if ($host === '') {
        throw new RuntimeException('TRADECORE_1_8_9_PUBLIC_BASE_URL fehlt.');
    }
    $https = ($_SERVER['HTTPS'] ?? '') !== '' && ($_SERVER['HTTPS'] ?? '') !== 'off';
    $directory = str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? '/')));
    return ($https ? 'https' : 'http') . '://' . $host . rtrim($directory, '/');
}
