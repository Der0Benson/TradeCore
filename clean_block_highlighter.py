from pathlib import Path

path = Path(r"c:\Users\pritz\IdeaProjects\Tradecorev2\src\client\java\Tradecorev2\client\BlockHighlighter.java")
text = path.read_text()
text = text.replace('\\\\u00A7', '\\u00A7')
text = text.replace('\u00A7', '\\u00A7')
text = text.replace('\\u00A7', '\\u00A7')
path.write_text(text)
