<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

function tc_discord_request(string $method, string $path, ?array $payload = null): array
{
    $token = tc_env('DISCORD_BOT_TOKEN');
    if ($token === null) {
        throw new RuntimeException('DISCORD_BOT_TOKEN fehlt.');
    }

    $curl = curl_init('https://discord.com/api/v10' . $path);
    $options = [
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 4,
        CURLOPT_TIMEOUT => 10,
        CURLOPT_HTTPHEADER => [
            'Authorization: Bot ' . $token,
            'Content-Type: application/json',
            'User-Agent: TradeCore-1.8.9-API/1.0',
        ],
    ];
    if ($payload !== null) {
        $options[CURLOPT_POSTFIELDS] = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }
    curl_setopt_array($curl, $options);
    $body = curl_exec($curl);
    if ($body === false) {
        $error = curl_error($curl);
        curl_close($curl);
        throw new RuntimeException('Discord ist nicht erreichbar: ' . $error);
    }
    $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    curl_close($curl);
    $decoded = $body === '' ? [] : json_decode($body, true);
    return ['status' => $status, 'body' => is_array($decoded) ? $decoded : []];
}

function tc_discord_member(string $discordId): bool
{
    $guildId = tc_env('DISCORD_GUILD_ID');
    if ($guildId === null) {
        throw new RuntimeException('DISCORD_GUILD_ID fehlt.');
    }
    return tc_discord_request('GET', "/guilds/{$guildId}/members/{$discordId}")['status'] === 200;
}

function tc_discord_send_link_dm(string $discordId, string $playerName, string $confirmationUrl): bool
{
    $dm = tc_discord_request('POST', '/users/@me/channels', ['recipient_id' => $discordId]);
    $channelId = (string) ($dm['body']['id'] ?? '');
    if ($dm['status'] !== 200 || $channelId === '') {
        return false;
    }

    $message = tc_discord_request('POST', "/channels/{$channelId}/messages", [
        'embeds' => [[
            'title' => 'TradeCore 1.8.9 Account-Verknüpfung',
            'description' => "Der Minecraft-Account **{$playerName}** möchte sich mit diesem Discord-Account verbinden.",
            'color' => 0x5865F2,
            'footer' => ['text' => 'Wenn du das nicht warst, ignoriere diese Nachricht.'],
        ]],
        'components' => [[
            'type' => 1,
            'components' => [[
                'type' => 2,
                'style' => 5,
                'label' => 'Ja, verknüpfen',
                'url' => $confirmationUrl,
            ]],
        ]],
    ]);
    return $message['status'] >= 200 && $message['status'] < 300;
}

function tc_bdt_embed(array $bdt, int $fastVotes, int $slowVotes): array
{
    $date = (string) ($bdt['bdt_date'] ?? date('Y-m-d'));
    if (!preg_match('/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/', $date)) {
        $date = date('Y-m-d');
    }
    $itemName = trim((string) ($bdt['item_name'] ?? 'Unbekannt'));
    $reward = rtrim(trim((string) ($bdt['gewinn'] ?? '-')), ". \t\n\r\0\x0B");

    return [
        'title' => '📦 Block des Tages: ' . $date,
        'description' => "Heute gibt es **{$itemName}** mit dem Gewinn **{$reward}.**\n\n"
            . "Votes: schnell: {$fastVotes}, langsam: {$slowVotes}",
        'color' => 0xFFFF00,
    ];
}

function tc_discord_announce_bdt(array $bdt, ?string $previousMessageId = null): ?string
{
    $channelId = tc_env('DISCORD_BDT_1_8_9_CHANNEL_ID');
    if ($channelId === null) {
        return null;
    }

    $previousMessageId = trim((string) $previousMessageId);
    if ($previousMessageId !== '') {
        $result = tc_discord_request(
            'PATCH',
            "/channels/{$channelId}/messages/{$previousMessageId}",
            ['embeds' => [tc_bdt_embed($bdt, 0, 0)]]
        );
        if ($result['status'] >= 200 && $result['status'] < 300) {
            return $previousMessageId;
        }
        // Wurde die alte Nachricht in Discord geloescht, darf eine neue angelegt werden.
        if ($result['status'] !== 404) {
            throw new RuntimeException('Discord-BdT-Nachricht vom Vortag konnte nicht aktualisiert werden.');
        }
    }

    $result = tc_discord_request('POST', "/channels/{$channelId}/messages", ['embeds' => [tc_bdt_embed($bdt, 0, 0)]]);
    if ($result['status'] < 200 || $result['status'] >= 300) {
        throw new RuntimeException('Discord-BdT-Nachricht konnte nicht gesendet werden.');
    }
    return isset($result['body']['id']) ? (string) $result['body']['id'] : null;
}

function tc_discord_update_bdt(array $bdt, int $fastVotes, int $slowVotes): void
{
    $channelId = tc_env('DISCORD_BDT_1_8_9_CHANNEL_ID');
    $messageId = (string) ($bdt['discord_message_id'] ?? '');
    if ($channelId === null || $messageId === '') {
        return;
    }
    $result = tc_discord_request(
        'PATCH',
        "/channels/{$channelId}/messages/{$messageId}",
        ['embeds' => [tc_bdt_embed($bdt, $fastVotes, $slowVotes)]]
    );
    if ($result['status'] < 200 || $result['status'] >= 300) {
        throw new RuntimeException('Discord-BdT-Nachricht konnte nicht aktualisiert werden.');
    }
}
