/**
 * ファイルチェックポイントデモ
 *
 * enableFileCheckpointing を有効にして、ファイル変更を追跡し、
 * CheckpointId + rewindFiles() で任意の時点に巻き戻すパターンを示す。
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.CheckpointId
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.Model
import me.matsumo.claude.agent.types.ResultMessage
import me.matsumo.claude.agent.types.UserMessage

suspend fun runFileCheckpointDemo() {
    println("=== ファイルチェックポイントデモ ===")
    println()

    println("■ enableFileCheckpointing の設定:")
    println("  createSession {")
    println("      enableFileCheckpointing = true  // チェックポイント機能を有効化")
    println("  }")
    println()

    println("■ チェックポイントの仕組み:")
    println("  1. セッション中の各 UserMessage には uuid が付与される")
    println("  2. uuid を CheckpointId として保存しておく")
    println("  3. rewindFiles(checkpointId) でその時点のファイル状態に巻き戻せる")
    println()

    println("■ 使用パターン:")
    println("  var checkpoint: CheckpointId? = null")
    println()
    println("  session.receiveResponse().collect { msg ->")
    println("      when (msg) {")
    println("          is UserMessage -> {")
    println("              // ツール結果の uuid をチェックポイントとして記録")
    println("              msg.uuid?.let { checkpoint = CheckpointId(it) }")
    println("          }")
    println("          else -> {}")
    println("      }")
    println("  }")
    println()
    println("  // ファイルを巻き戻す")
    println("  checkpoint?.let { session.rewindFiles(it) }")
    println()

    // 実際の実行
    try {
        createSession {
            model = Model.SONNET
            maxTurns = 3
            bypassPermissions()
            enableFileCheckpointing = true
        }.use { session ->
            session.connect()

            // 1ターン目: ファイルを作成させる
            session.send("/tmp/demo_checkpoint.txt に「Hello」と書いてください")
            var checkpoint: CheckpointId? = null

            session.receiveResponse().collect { msg ->
                when (msg) {
                    is UserMessage -> {
                        msg.uuid?.let {
                            checkpoint = CheckpointId(it)
                            println("  チェックポイント記録: $it")
                        }
                    }
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n  [1ターン目完了]")
                    else -> {}
                }
            }

            // 2ターン目: ファイルを変更させる
            session.send("同じファイルの内容を「Goodbye」に書き換えてください")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n  [2ターン目完了]")
                    else -> {}
                }
            }

            // チェックポイントに巻き戻し
            checkpoint?.let {
                println("  → rewindFiles() でチェックポイントに巻き戻し...")
                session.rewindFiles(it)
                println("  → 巻き戻し完了！ファイルは「Hello」の状態に復帰")
            }
        }
    } catch (e: Exception) {
        println("  ※ CLI未接続のため実行スキップ: ${e.message}")
    }

    println()
}
