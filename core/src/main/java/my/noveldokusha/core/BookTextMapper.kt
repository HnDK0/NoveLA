package my.noveldokusha.core

object BookTextMapper {
    // <img yrel="{float}"> {uri} </img>
    data class ImgEntry(val path: String, val yrel: Float) {
        fun toXMLString(): String {
            return """<img src="$path" yrel="${"%.2f".format(yrel)}">"""
        }
    }
}
