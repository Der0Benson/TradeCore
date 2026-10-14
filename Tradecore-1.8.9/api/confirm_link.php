<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

$success = false;
$title = 'Verknüpfung fehlgeschlagen';
$message = 'Der Bestätigungslink ist ungültig oder abgelaufen.';
$token = trim((string) ($_GET['token'] ?? ''));

if (preg_match('/^[a-f0-9]{64}$/', $token)) {
    $pdo = null;
    try {
        $pdo = tc_db();
        $pdo->beginTransaction();
        $statement = $pdo->prepare(
            'SELECT token, player_uuid, player_name, discord_id
             FROM ' . TC_VERIFICATION_TABLE . '
             WHERE token = :token AND expires_at > NOW()
             LIMIT 1 FOR UPDATE'
        );
        $statement->execute([':token' => $token]);
        $request = $statement->fetch();
        if (!$request) {
            throw new RuntimeException('Token nicht gefunden oder abgelaufen.');
        }

        $conflict = $pdo->prepare(
            'SELECT id FROM users
             WHERE (minecraft_uuid = :uuid AND discord_id <> :discord_id)
                OR (discord_id = :discord_id_2 AND minecraft_uuid IS NOT NULL AND minecraft_uuid <> :uuid_2)
             LIMIT 1'
        );
        $conflict->execute([
            ':uuid' => $request['player_uuid'],
            ':uuid_2' => $request['player_uuid'],
            ':discord_id' => $request['discord_id'],
            ':discord_id_2' => $request['discord_id'],
        ]);
        if ($conflict->fetch()) {
            throw new RuntimeException('Einer der Accounts wurde inzwischen anderweitig verknüpft.');
        }

        $update = $pdo->prepare(
            'UPDATE users
             SET minecraft_uuid = :uuid, minecraft_name = :player_name
             WHERE discord_id = :discord_id
               AND (minecraft_uuid IS NULL OR minecraft_uuid = :uuid_2)'
        );
        $update->execute([
            ':uuid' => $request['player_uuid'],
            ':uuid_2' => $request['player_uuid'],
            ':player_name' => $request['player_name'],
            ':discord_id' => $request['discord_id'],
        ]);
        if ($update->rowCount() < 1) {
            $check = $pdo->prepare(
                'SELECT 1 FROM users WHERE discord_id = :discord_id AND minecraft_uuid = :uuid LIMIT 1'
            );
            $check->execute([':discord_id' => $request['discord_id'], ':uuid' => $request['player_uuid']]);
            if (!$check->fetchColumn()) {
                throw new RuntimeException('Gemeinsamer Benutzer konnte nicht aktualisiert werden.');
            }
        }

        $pdo->prepare('DELETE FROM ' . TC_VERIFICATION_TABLE . ' WHERE token = :token')->execute([':token' => $token]);
        $pdo->commit();
        $success = true;
        $title = 'Accounts verknüpft';
        $message = 'Dein gemeinsames TradeCore-Konto ist jetzt auch in der 1.8.9-Mod verifiziert.';
    } catch (Throwable $error) {
        if ($pdo instanceof PDO && $pdo->inTransaction()) {
            $pdo->rollBack();
        }
        tc_log('confirm_link', $error->getMessage());
    }
}

http_response_code($success ? 200 : 400);
header('Content-Type: text/html; charset=utf-8');
?>
<!doctype html>
<html lang="de">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><?= htmlspecialchars($title, ENT_QUOTES, 'UTF-8') ?> - TradeCore 1.8.9</title>
    <style>
        :root { color-scheme: dark; font-family: system-ui, sans-serif; }
        body { min-height: 100vh; margin: 0; display: grid; place-items: center; background: #10131a; color: #f5f7fb; }
        main { width: min(560px, calc(100% - 40px)); padding: 32px; border: 1px solid #2b3342; border-radius: 16px; background: #191e28; box-shadow: 0 20px 60px #0008; }
        .status { color: <?= $success ? '#65d68a' : '#ff7474' ?>; font-weight: 700; }
        h1 { margin: 8px 0 12px; }
        p { margin: 0; color: #c8cfda; line-height: 1.55; }
    </style>
</head>
<body>
<main>
    <div class="status">TradeCore 1.8.9</div>
    <h1><?= htmlspecialchars($title, ENT_QUOTES, 'UTF-8') ?></h1>
    <p><?= htmlspecialchars($message, ENT_QUOTES, 'UTF-8') ?></p>
</main>
</body>
</html>
