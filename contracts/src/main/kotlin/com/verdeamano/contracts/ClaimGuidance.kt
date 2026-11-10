package com.verdeamano.contracts

/** Public orientation only. Store proposals live in the backend and are never applied by this content. */
object ClaimGuidance {
    const val notice="Orientación informativa; no es una política comercial aprobada ni una decisión sobre tu caso. La elegibilidad se revisa según el producto, la operación y las normas aplicables."
    fun text(kind:ClaimKind)=when(kind){
        ClaimKind.GARANTIA->"La garantía cubre calidad e idoneidad. Primero aplica el término legal o especial; a falta de regla y término informado, para productos nuevos es un año. En perecederos corresponde a su fecha de vencimiento o expiración (Ley 1480, art. 8). La solución depende de la falla y del bien; no se promete siempre reparación previa."
        ClaimKind.RETRACTO->"En ventas a distancia, el término general para ejercer retracto es cinco días hábiles desde la entrega, con las excepciones legales aplicables. En comercio electrónico, la Ley 2439 de 2024, art. 3, prevé hasta quince días calendario para devolver el dinero tras ejercer el derecho y cumplir las obligaciones de datos y devolución del producto."
        ClaimKind.REVERSION->"La reversión del pago electrónico tiene causales y trámite propios. Se requiere reclamación al proveedor y notificación al emisor dentro del término aplicable. No equivale a un reembolso automático de la plataforma (Ley 1480, art. 51; Decreto 587 de 2016)."
        ClaimKind.PQR->"Puedes comunicar hechos e inconformidades y consultar su trámite. La clasificación y el término de respuesta requieren revisión según la naturaleza de la petición; no se fija un plazo universal para todos los casos."
        ClaimKind.DEVOLUCION->"Una devolución comercial voluntaria requiere condiciones acordadas entre las partes. No reemplaza ni limita los derechos de garantía, retracto o reversión; no se anuncia aquí una política voluntaria vigente."
    }
}
