<?php

declare(strict_types=1);

header('Cache-Control: public, max-age=60, stale-while-revalidate=30');
header('Content-Type: application/json; charset=utf-8');

$requestedVersion = trim((string) ($_GET['minecraftVersion'] ?? '1.8.9'));
if ($requestedVersion !== '1.8.9') {
    http_response_code(400);
    echo json_encode(['success' => false, 'message' => 'Dieser Endpunkt ist nur für Minecraft 1.8.9.']);
    exit;
}

echo json_encode([
    'success' => true,
    'latest' => '2.2.12-1.8.9',
    'minecraft_version' => '1.8.9',
], JSON_UNESCAPED_SLASHES);
