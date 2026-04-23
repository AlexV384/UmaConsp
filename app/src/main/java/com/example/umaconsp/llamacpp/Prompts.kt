package com.example.umaconsp.llamacpp

class Prompts {
}
const val DEFAULT_JSON = """Write down what you see on this image. Answer only with raw JSON. Write ALL TEXT that IS POSSIBLE to see.
Every line of text is a json object.
Example lines:
{
    "text": "Привет, мир!",
    "style": "normal",   // either: normal, bold, italic, underline
    "alignment": "left"  // either: left, center, right
}
{
    "text": "f(x) = 2x + 4 - (1 / x)",
    "style": "normal",
    "alignment": "left"
}
{
    "text": "Производная: f'(x) = 2 + (1/x^2)",
    "style": "normal",
    "alignment": "left"
}
{
    "text": "Заметки",
    "style": "italic",
    "alignment": "center"
}
{
    "text": "Fusion has been used on over 1000 major Hollywood blockbuster feature films.",
    "style": "normal",
    "alignment": "left"
}"""
const val MINERU_TEXT = "\nText Recognition:"
const val MINERU_LAYOUT = "\nLayout Detection:"
const val MINERU_TABLE = "\nTable Recognition:"
const val MINERU_EQUATION = "\nFormula Recognition:"
const val GLM_TEXT = "\nText Recognition:"