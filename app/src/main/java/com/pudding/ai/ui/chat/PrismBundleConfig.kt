package com.pudding.ai.ui.chat

import io.noties.prism4j.annotations.PrismBundle

/**
 * Prism4j 配置 - KSP 会自动生成 GrammarLocator 类
 * 生成的类名: .PrismGrammarLocator (同包名)
 * 
 * 支持的语言: c, clike, clojure, cpp, csharp, css, dart, git, go, groovy,
 *             java, javascript, json, kotlin, latex, makefile, markdown,
 *             markup, python, scala, sql, swift, yaml
 */
@PrismBundle(
    include = [
        "kotlin", "java", "javascript", "python", "json",
        "markup", "css", "sql", "c", "cpp"
    ],
    grammarLocatorClassName = ".PrismGrammarLocator"
)
class PrismBundleConfig