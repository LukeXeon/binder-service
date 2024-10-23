package binderservice

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class BinderService(
    val name: String,
    val process: String = "",
)
