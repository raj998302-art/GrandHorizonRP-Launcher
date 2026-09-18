.class final Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;
.super Lkotlin/coroutines/jvm/internal/SuspendLambda;
.source "DownloadWorker.kt"

# interfaces
.implements Lkotlin/jvm/functions/Function2;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/blackhub/bronline/launcher/download/DownloadWorker;->downloadFile(Lcom/blackhub/bronline/launcher/data/MyFile;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x18
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Lkotlin/coroutines/jvm/internal/SuspendLambda;",
        "Lkotlin/jvm/functions/Function2<",
        "Lkotlinx/coroutines/CoroutineScope;",
        "Lkotlin/coroutines/Continuation<",
        "-",
        "Lkotlin/Unit;",
        ">;",
        "Ljava/lang/Object;",
        ">;"
    }
.end annotation

.annotation system Ldalvik/annotation/SourceDebugExtension;
    value = "SMAP\nDownloadWorker.kt\nKotlin\n*S Kotlin\n*F\n+ 1 DownloadWorker.kt\ncom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2\n+ 2 builders.kt\nio/ktor/client/request/BuildersKt\n+ 3 builders.kt\nio/ktor/client/request/BuildersKt$prepareGet$4\n*L\n1#1,400:1\n657#2,4:401\n463#2:405\n661#2,2:406\n663#2:409\n287#2,2:410\n52#2:412\n659#3:408\n*S KotlinDebug\n*F\n+ 1 DownloadWorker.kt\ncom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2\n*L\n327#1:401,4\n327#1:405\n327#1:406,2\n327#1:409\n327#1:410,2\n327#1:412\n327#1:408\n*E\n"
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\n"
    }
    d2 = {
        "<anonymous>",
        "",
        "Lkotlinx/coroutines/CoroutineScope;"
    }
    k = 0x3
    mv = {
        0x2,
        0x2,
        0x0
    }
    xi = 0x30
.end annotation

.annotation runtime Lkotlin/coroutines/jvm/internal/DebugMetadata;
    c = "com.blackhub.bronline.launcher.download.DownloadWorker$downloadFile$2"
    f = "DownloadWorker.kt"
    i = {
        0x0,
        0x0
    }
    l = {
        0x148
    }
    m = "invokeSuspend"
    n = {
        "path",
        "file"
    }
    s = {
        "L$0",
        "L$1"
    }
.end annotation

.annotation build Lkotlin/jvm/internal/SourceDebugExtension;
    value = {
        "SMAP\nDownloadWorker.kt\nKotlin\n*S Kotlin\n*F\n+ 1 DownloadWorker.kt\ncom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2\n+ 2 builders.kt\nio/ktor/client/request/BuildersKt\n+ 3 builders.kt\nio/ktor/client/request/BuildersKt$prepareGet$4\n*L\n1#1,400:1\n657#2,4:401\n463#2:405\n661#2,2:406\n663#2:409\n287#2,2:410\n52#2:412\n659#3:408\n*S KotlinDebug\n*F\n+ 1 DownloadWorker.kt\ncom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2\n*L\n327#1:401,4\n327#1:405\n327#1:406,2\n327#1:409\n327#1:410,2\n327#1:412\n327#1:408\n*E\n"
    }
.end annotation


# instance fields
.field final synthetic $myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

.field L$0:Ljava/lang/Object;

.field L$1:Ljava/lang/Object;

.field label:I

.field final synthetic this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;


# direct methods
.method constructor <init>(Lcom/blackhub/bronline/launcher/download/DownloadWorker;Lcom/blackhub/bronline/launcher/data/MyFile;Lkotlin/coroutines/Continuation;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/blackhub/bronline/launcher/download/DownloadWorker;",
            "Lcom/blackhub/bronline/launcher/data/MyFile;",
            "Lkotlin/coroutines/Continuation<",
            "-",
            "Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;",
            ">;)V"
        }
    .end annotation

    .line 0
    iput-object p1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;

    iput-object p2, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    const/4 p1, 0x2

    invoke-direct {p0, p1, p3}, Lkotlin/coroutines/jvm/internal/SuspendLambda;-><init>(ILkotlin/coroutines/Continuation;)V

    return-void
.end method


# virtual methods
.method public final create(Ljava/lang/Object;Lkotlin/coroutines/Continuation;)Lkotlin/coroutines/Continuation;
    .locals 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Object;",
            "Lkotlin/coroutines/Continuation<",
            "*>;)",
            "Lkotlin/coroutines/Continuation<",
            "Lkotlin/Unit;",
            ">;"
        }
    .end annotation

    .line 0
    new-instance p1, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;

    iget-object v0, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;

    iget-object v1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    invoke-direct {p1, v0, v1, p2}, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;-><init>(Lcom/blackhub/bronline/launcher/download/DownloadWorker;Lcom/blackhub/bronline/launcher/data/MyFile;Lkotlin/coroutines/Continuation;)V

    return-object p1
.end method

.method public bridge synthetic invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 0

    .line 0
    check-cast p1, Lkotlinx/coroutines/CoroutineScope;

    check-cast p2, Lkotlin/coroutines/Continuation;

    invoke-virtual {p0, p1, p2}, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->invoke(Lkotlinx/coroutines/CoroutineScope;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invoke(Lkotlinx/coroutines/CoroutineScope;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lkotlinx/coroutines/CoroutineScope;",
            "Lkotlin/coroutines/Continuation<",
            "-",
            "Lkotlin/Unit;",
            ">;)",
            "Ljava/lang/Object;"
        }
    .end annotation

    .line 0
    invoke-virtual {p0, p1, p2}, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->create(Ljava/lang/Object;Lkotlin/coroutines/Continuation;)Lkotlin/coroutines/Continuation;

    move-result-object p1

    check-cast p1, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;

    sget-object p2, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    invoke-virtual {p1, p2}, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 8

    invoke-static {}, Lkotlin/coroutines/intrinsics/IntrinsicsKt;->getCOROUTINE_SUSPENDED()Ljava/lang/Object;

    move-result-object v0

    .line 316
    iget v1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->label:I

    const/4 v2, 0x1

    if-eqz v1, :cond_1

    if-ne v1, v2, :cond_0

    iget-object v0, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->L$1:Ljava/lang/Object;

    check-cast v0, Ljava/io/File;

    iget-object v0, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->L$0:Ljava/lang/Object;

    check-cast v0, Ljava/lang/String;

    :try_start_0
    invoke-static {p1}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto/16 :goto_0

    :cond_0
    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "call to \'resume\' before \'invoke\' with coroutine"

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_1
    invoke-static {p1}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    .line 317
    iget-object p1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;

    iget-object v1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    invoke-virtual {v1}, Lcom/blackhub/bronline/launcher/data/MyFile;->getPath()Ljava/lang/String;

    move-result-object v1

    invoke-static {p1, v1}, Lcom/blackhub/bronline/launcher/download/DownloadWorker;->access$createDirAndReturnPath(Lcom/blackhub/bronline/launcher/download/DownloadWorker;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    .line 319
    new-instance v1, Ljava/io/File;

    iget-object v3, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;

    invoke-static {v3}, Lcom/blackhub/bronline/launcher/download/DownloadWorker;->access$getGamePath$p(Lcom/blackhub/bronline/launcher/download/DownloadWorker;)Ljava/lang/String;

    move-result-object v3

    iget-object v4, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    invoke-virtual {v4}, Lcom/blackhub/bronline/launcher/data/MyFile;->getName()Ljava/lang/String;

    move-result-object v4

    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v5, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-direct {v1, v3}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 320
    invoke-virtual {v1}, Ljava/io/File;->getParentFile()Ljava/io/File;

    move-result-object v3

    if-eqz v3, :cond_2

    invoke-virtual {v3}, Ljava/io/File;->mkdirs()Z

    move-result v3

    invoke-static {v3}, Lkotlin/coroutines/jvm/internal/Boxing;->boxBoolean(Z)Ljava/lang/Boolean;

    .line 321
    :cond_2
    invoke-virtual {v1}, Ljava/io/File;->delete()Z

    .line 322
    invoke-virtual {v1}, Ljava/io/File;->createNewFile()Z

    .line 324
    iget-object v3, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    new-instance v4, Ljava/lang/StringBuilder;

    invoke-direct {v4}, Ljava/lang/StringBuilder;-><init>()V

    const-string/jumbo v5, "start download: "

    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Lcom/blackhub/bronline/game/core/utils/UtilsKt;->crashlyticsLog(Ljava/lang/String;)V

    .line 327
    :try_start_1
    iget-object v3, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;

    invoke-static {v3}, Lcom/blackhub/bronline/launcher/download/DownloadWorker;->access$getClient(Lcom/blackhub/bronline/launcher/download/DownloadWorker;)Lio/ktor/client/HttpClient;

    move-result-object v3

    sget-object v4, Lcom/blackhub/bronline/launcher/Settings;->INSTANCE:Lcom/blackhub/bronline/launcher/Settings;

    invoke-virtual {v4}, Lcom/blackhub/bronline/launcher/Settings;->getCURRENT_CDN_URL()Ljava/lang/String;

    move-result-object v4

    iget-object v5, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    invoke-virtual {v5}, Lcom/blackhub/bronline/launcher/data/MyFile;->getPath()Ljava/lang/String;

    move-result-object v5

    const/16 v6, 0x2f

    const/16 v7, 0x2e

    invoke-virtual {v5, v6, v7}, Ljava/lang/String;->replace(CC)Ljava/lang/String;

    move-result-object v5

    iget-object v6, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    invoke-virtual {v6}, Lcom/blackhub/bronline/launcher/data/MyFile;->getName()Ljava/lang/String;

    move-result-object v6

    new-instance v7, Ljava/lang/StringBuilder;

    invoke-direct {v7}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v7, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v7, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v7, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v7}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v4

    .line 405
    new-instance v5, Lio/ktor/client/request/HttpRequestBuilder;

    invoke-direct {v5}, Lio/ktor/client/request/HttpRequestBuilder;-><init>()V

    .line 406
    invoke-static {v5, v4}, Lio/ktor/client/request/HttpRequestKt;->url(Lio/ktor/client/request/HttpRequestBuilder;Ljava/lang/String;)V

    .line 410
    sget-object v4, Lio/ktor/http/HttpMethod;->Companion:Lio/ktor/http/HttpMethod$Companion;

    invoke-virtual {v4}, Lio/ktor/http/HttpMethod$Companion;->getGet()Lio/ktor/http/HttpMethod;

    move-result-object v4

    invoke-virtual {v5, v4}, Lio/ktor/client/request/HttpRequestBuilder;->setMethod(Lio/ktor/http/HttpMethod;)V

    .line 412
    new-instance v4, Lio/ktor/client/statement/HttpStatement;

    invoke-direct {v4, v5, v3}, Lio/ktor/client/statement/HttpStatement;-><init>(Lio/ktor/client/request/HttpRequestBuilder;Lio/ktor/client/HttpClient;)V

    .line 328
    new-instance v3, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2$1;

    iget-object v5, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;

    iget-object v6, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    const/4 v7, 0x0

    invoke-direct {v3, v1, v5, v6, v7}, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2$1;-><init>(Ljava/io/File;Lcom/blackhub/bronline/launcher/download/DownloadWorker;Lcom/blackhub/bronline/launcher/data/MyFile;Lkotlin/coroutines/Continuation;)V

    invoke-static {p1}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    iput-object p1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->L$0:Ljava/lang/Object;

    invoke-static {v1}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    iput-object p1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->L$1:Ljava/lang/Object;

    iput v2, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->label:I

    invoke-virtual {v4, v3, p0}, Lio/ktor/client/statement/HttpStatement;->execute(Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object p1
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0

    if-ne p1, v0, :cond_3

    return-object v0

    .line 394
    :catch_0
    iget-object p1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->this$0:Lcom/blackhub/bronline/launcher/download/DownloadWorker;

    invoke-virtual {p1}, Lcom/blackhub/bronline/launcher/download/DownloadWorker;->getLauncherDatabase()Lcom/blackhub/bronline/launcher/database/LauncherDatabase;

    move-result-object p1

    invoke-virtual {p1}, Lcom/blackhub/bronline/launcher/database/LauncherDatabase;->myFileDao()Lcom/blackhub/bronline/launcher/database/MyFileDao;

    move-result-object p1

    .line 395
    iget-object v0, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    invoke-virtual {v0}, Lcom/blackhub/bronline/launcher/data/MyFile;->getPath()Ljava/lang/String;

    move-result-object v0

    iget-object v1, p0, Lcom/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2;->$myFile:Lcom/blackhub/bronline/launcher/data/MyFile;

    invoke-virtual {v1}, Lcom/blackhub/bronline/launcher/data/MyFile;->getName()Ljava/lang/String;

    move-result-object v1

    const/4 v2, 0x0

    invoke-interface {p1, v2, v0, v1}, Lcom/blackhub/bronline/launcher/database/MyFileDao;->setMyFileDownloadedByPathAndName(ZLjava/lang/String;Ljava/lang/String;)V

    .line 397
    :cond_3
    :goto_0
    sget-object p1, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object p1
.end method
