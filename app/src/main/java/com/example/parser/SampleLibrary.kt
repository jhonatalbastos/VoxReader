package com.example.parser

import com.example.data.BookEntity
import com.example.data.ChapterEntity

object SampleLibrary {

    fun getSampleBooks(): List<Pair<BookEntity, List<ChapterEntity>>> {
        return listOf(
            createDomCasmurro(),
            createMetamorfose(),
            createArteDaGuerra(),
            createOCortico()
        )
    }

    private fun createDomCasmurro(): Pair<BookEntity, List<ChapterEntity>> {
        val book = BookEntity(
            title = "Dom Casmurro",
            author = "Machado de Assis",
            path = "assets://books/dom_casmurro.epub",
            progress = 0.05f,
            format = "EPUB",
            coverGradientStart = 0xFF1E1B4B, // Deep Indigo
            coverGradientEnd = 0xFF4338CA,
            totalChapters = 4,
            currentChapterIndex = 0,
            readingProgress = 0.05f,
            isFavorite = true,
            voiceId = "pt-BR-FranciscaNeural",
            voiceSpeed = 1.05f,
            voicePitch = 1.0f
        )

        val chapters = listOf(
            ChapterEntity(
                bookId = 0,
                chapterIndex = 0,
                title = "Capítulo I — Do Título",
                content = """Uma noite destas, vindo da cidade para o Engenho Novo, encontrei num trem da Central um rapaz aqui do bairro, que eu conheço de vista e de chapéu. Cumprimentou-me, sentou-se ao pé de mim, falou da lua e dos ministros, e acabou recitando-me versos.

A viagem era curta, e os versos pode ser que não fossem inteiramente maus, porém o caso é que eu ia cansado, fechei os olhos, cochilei. Acharam que eu dormia e deixaram-me em paz. No dia seguinte entraram a dizer de mim que eu era um casmurro.

Não consultes dicionários. Casmurro não está aqui no sentido que eles lhe dão, mas no que lhe pôs o vulgo de homem calado e metido consigo. Dom veio por ironia, para atribuir-me fumos de fidalgo. Tudo por estar cochilando!

Também não achei melhor título para a minha narração; se não tiver outro daqui até ao fim do livro, vai este mesmo. O meu fim evidente era atar as duas pontas da vida, e restaurar na velhice a adolescência."""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 1,
                title = "Capítulo II — Do Livro",
                content = """Agora que expliquei o título, passo a escrever o livro. Antes disso, porém, digamos os motivos que me puseram a pena na mão.

Moro numa casa que é a cópia fiel daquela em que me criei na antiga Rua de Matacavalos. O mesmo aspecto, a mesma distribuição de cômodos, as mesmas pinturas do teto: numa sala quatro bustos de César, Augusto, Nero e Massinissa; na outra passarinhos entre flores.

Tudo isso fiz para recriar o passado. Pois bem, nem o passado veio, nem a casa fez reviver as sensações de outrora. Viver só não é a mesma coisa que viver outrora com os meus entes queridos. Então imaginei escrever estas memórias. Quem sabe se a ilusão da escrita não me trará o calor da mocidade?"""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 2,
                title = "Capítulo III — A Denúncia",
                content = """Ia a entrar na sala de visitas, quando ouvi proferir o meu nome e escondi-me atrás da porta. A conversa era entre minha mãe e José Dias.

— D. Glória, a senhora persiste na ideia de meter o Bentinho no seminário? Já é tempo, e receio que haja agora uma dificuldade.

— Que dificuldade?

— Uma grande dificuldade. Bentinho anda muito pelos cantos com a filha do Tartaruga, e andam de cochichos. Não digo que haja maldade neles, mas é bom prevenir. A amizade dos dois já é namorico de adolescentes.

Minha mãe ficou pálida. Lembrava-se da promessa que fizera a Deus, de me dar à Igreja se tivesse um filho varão. Tremi de medo e de júbilo. Medo do seminário, e júbilo ao ouvir pela primeira vez que Capitu me amava."""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 3,
                title = "Capítulo IV — Um Dever Estrito",
                content = """José Dias saiu da sala com passos compassados. Fiquei ali encostado à parede, ouvindo as batidas do meu coração. Bentinho e Capitu! Aquelas palavras ecoavam nos meus ouvidos como clarins de festa.

Eu amava Capitu, e não sabia! Ela tinha catorze anos, eu quinze; brincávamos desde pequenos, mas agora o mundo inteiro mudava de cor.

Corri para o quintal. Queria vê-la imediatamente no muro divisório entre as nossas casas. No caminho, olhei para o céu azul do Rio de Janeiro e senti uma estranha sensação de liberdade misturada ao terror do seminário."""
            )
        )

        return Pair(book, chapters)
    }

    private fun createMetamorfose(): Pair<BookEntity, List<ChapterEntity>> {
        val book = BookEntity(
            title = "A Metamorfose",
            author = "Franz Kafka",
            path = "assets://books/a_metamorfose.txt",
            progress = 0.0f,
            format = "TXT",
            coverGradientStart = 0xFF701A75, // Deep Fuchsia/Purple
            coverGradientEnd = 0xFF3B0764,
            totalChapters = 3,
            currentChapterIndex = 0,
            readingProgress = 0.0f,
            isFavorite = true,
            voiceId = "pt-BR-AntonioNeural",
            voiceSpeed = 1.10f,
            voicePitch = 0.95f
        )

        val chapters = listOf(
            ChapterEntity(
                bookId = 0,
                chapterIndex = 0,
                title = "Parte I — O Despertar Estranho",
                content = """Quando certa manhã Gregor Samsa acordou de sonhos intranquilos, encontrou-se em sua cama metamorfoseado num inseto monstruoso.

Estava deitado sobre suas costas duras como couraça e, ao levantar um pouco a cabeça, viu seu ventre abaulado, marrom, dividido em segmentos arqueados. Sobre o cimo do ventre, a colcha quase escorregara por completo. Suas inúmeras pernas, lamentavelmente finas em comparação com o resto de seu volume, oscilavam desamparadas diante de seus olhos.

"O que aconteceu comigo?", pensou ele. Não era um sonho. Seu quarto, um autêntico quarto humano, apenas um pouco pequeno demais, repousava calmo entre as quatro paredes bem conhecidas.

Sobre a mesa achava-se espalhado um mostruário desempacotado de tecidos de lã — Samsa era caixeiro-viajante —, e acima dela estava pendurada a estampa que recortara havia pouco de uma revista ilustrada. Mostrava uma senhora vestida com chapéu e boá de pele.

Gregor voltou os olhos para a janela. O tempo sombrio — ouviam-se as gotas de chuva batendo no zinco da calha — encheu-o de uma profunda melancolia."""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 1,
                title = "Parte II — A Nova Existência",
                content = """Gregor só acordou de seu sono profundo no início da noite. Certamente não teria acordado muito mais tarde mesmo sem perturbação, pois sentia-se bastante descansado, embora suas pernas ainda protestassem quando tentava mover-se.

Pela fresta sob a porta vinha um cheiro delicioso. Alguém colocara ali uma tigela com leite fresco no qual flutuavam pequenos pedaços de pão branco. Ele quase chorou de alegria, pois sua fome agora era muito maior do que pela manhã.

Mergulhou a cabeça no leite quase até os olhos. Mas logo a retirou desapontado: o leite, que sempre fora sua bebida favorita e que sua irmã carinhosamente lhe pusera ali, quase não lhe apetecia. Na verdade, sentia repugnância por aquele alimento humano que outrora tanto adorava.

Gregor rastejou sob o canapé, onde se sentia protegido da imensidão ameaçadora do teto de seu quarto."""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 2,
                title = "Parte III — O Som do Violino",
                content = """Certa noite, a porta da sala de estar permaneceu aberta. Gregor ouviu a irmã afinando o violino. Os novos inquilinos sentaram-se em cadeiras confortáveis para ouvi-la.

Gregor, atraído pela música doce, arrastou-se cautelosamente para fora de seu quarto empoeirado. Será ele um animal, se a música o comovia tanto? Parecia-lhe que o caminho para o alimento desconhecido e tão desejado se abria à sua frente.

Decidira avançar até a irmã, puxá-la pela saia e indicar-lhe que viesse para o seu quarto com o violino, pois ninguém na sala sabia apreciar o seu toque tão bem quanto ele.

Porém, um dos inquilinos percebeu a sua presença e apontou com o dedo trêmulo. O caos se instalou na casa dos Samsa."""
            )
        )

        return Pair(book, chapters)
    }

    private fun createArteDaGuerra(): Pair<BookEntity, List<ChapterEntity>> {
        val book = BookEntity(
            title = "A Arte da Guerra",
            author = "Sun Tzu",
            path = "assets://books/a_arte_da_guerra.md",
            progress = 0.0f,
            format = "MD",
            coverGradientStart = 0xFF7F1D1D, // Deep Crimson
            coverGradientEnd = 0xFFB91C1C,
            totalChapters = 3,
            currentChapterIndex = 0,
            readingProgress = 0.0f,
            isFavorite = false,
            voiceId = "pt-BR-DonatoNeural",
            voiceSpeed = 0.95f,
            voicePitch = 0.95f
        )

        val chapters = listOf(
            ChapterEntity(
                bookId = 0,
                chapterIndex = 0,
                title = "Capítulo I — Cálculos Preliminares",
                content = """A arte da guerra é de importância vital para o Estado. É uma questão de vida ou de morte, um caminho que leva tanto à segurança quanto à ruína. Portanto, é indispensável estudá-la minuciosamente.

Avalie a situação militar de acordo com cinco fatores fundamentais: o Caminho Moral, as Condições Climáticas, o Terreno, a Liderança do Comandante e a Doutrina Militar.

O Caminho Moral faz com que o povo esteja em completa harmonia com seus líderes, de modo que os sigam sem temer por suas vidas, indiferentes a qualquer perigo.

O Clima significa a noite e o dia, o frio e o calor, as estações do ano e os caprichos dos céus. O Terreno compreende distâncias, facilidade ou dificuldade de locomoção, se o campo é aberto ou estreito, garantindo chances de vida ou morte.

Toda estratégia militar repousa sobre a ilusão. Portanto, quando formos capazes de atacar, devemos parecer incapazes; quando empregamos nossas forças, devemos parecer inativos."""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 1,
                title = "Capítulo II — Da Condução da Guerra",
                content = """Nas operações bélicas, onde há mil carros velozes, mil carros pesados e cem mil soldados com armadura, o custo de mantê-los tanto no front quanto na retaguarda é astronômico.

Por isso, na guerra, que seu grande objetivo seja a vitória rápida, e não campanhas prolongadas. Não há registro de um país que tenha se beneficiado de uma guerra prolongada.

Aquele que compreende os males da guerra é o único capaz de compreender verdadeiramente o modo mais vantajoso de conduzi-la.

O general prudente procura alimentar-se dos recursos do inimigo. Um saco de mantimentos capturado ao adversário equivale a vinte dos próprios transportados por longas distâncias."""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 2,
                title = "Capítulo III — A Estratégia Ofensiva",
                content = """Na arte prática da guerra, a melhor coisa é capturar o país do inimigo intacto; destruí-lo é inferior. Capturar o exército inteiro é preferível a aniquilá-lo.

Assim, lutar e vencer em todas as batalhas não é o ápice da excelência; a suprema excelência consiste em quebrar a resistência do inimigo sem sequer lutar.

A forma mais elevada de liderança militar é frustrar os planos do adversário; a seguinte é impedir a junção das suas forças; a próxima é atacar o exército no campo; e a pior de todas é sitiar cidades muradas.

Conheça o seu inimigo e conheça a si mesmo, e em cem batalhas você nunca estará em perigo. Se você desconhece o inimigo mas conhece a si mesmo, para cada vitória terá uma derrota. Se não conhece o inimigo nem a si mesmo, sucumbirá em todas as batalhas."""
            )
        )

        return Pair(book, chapters)
    }

    private fun createOCortico(): Pair<BookEntity, List<ChapterEntity>> {
        val book = BookEntity(
            title = "O Cortiço",
            author = "Aluísio Azevedo",
            path = "assets://books/o_cortico.txt",
            progress = 0.0f,
            format = "TXT",
            coverGradientStart = 0xFF065F46, // Deep Emerald
            coverGradientEnd = 0xFF047857,
            totalChapters = 2,
            currentChapterIndex = 0,
            readingProgress = 0.0f,
            isFavorite = false,
            voiceId = "pt-BR-ThalitaNeural",
            voiceSpeed = 1.15f,
            voicePitch = 1.05f
        )

        val chapters = listOf(
            ChapterEntity(
                bookId = 0,
                chapterIndex = 0,
                title = "Capítulo I — A Ambição de João Romão",
                content = """João Romão foi, dos treze aos vinte e cinco anos, empregado de um vendeiro no bairro de Botafogo. O patrão comeu e bebeu até rebentar de uma congestão; o caixeiro trabalhou como um mouro, poupou cada tostão e comprou a venda com as suas economias.

Dali por diante a sua vida foi um contínuo martírio de avareza e ambição. Dormia na venda sobre um balcão, não gastava um real em roupas, comia as sobras dos pratos dos fregueses.

Junto à venda estendia-se um terreno espaçoso e abandonado. João Romão começou a construir ali casebres de madeira e alvenaria, alugando-os a trabalhadores da pedreira vizinha, lavadeiras e gente humilde.

Assim nasceu e floresceu o cortiço de São Romão, fervilhando de vida, cores, pregões e o ritmo febril da cidade do Rio de Janeiro do século dezenove."""
            ),
            ChapterEntity(
                bookId = 0,
                chapterIndex = 1,
                title = "Capítulo II — O Despertar da Estalagem",
                content = """Às cinco horas da manhã o cortiço acordava em peso. Abriam-se as portas com estrondo rangendo nas dobradiças enferrujadas.

Mulheres saíam de chinelas, de saias curtas e peitos à mostra, correndo com baldes e bacias para as bicas coletivas. Uma fila tumultuosa se formava em torno da água fresca.

Ouvia-se o bater compassado das roupas nas pedras das lavanderias, as canções portuguesas misturadas ao lundu brasileiro, o choro das crianças acordadas cedo e as discussões acaloradas dos vizinhos.

O sol rompia sobre os telhados avermelhados, acendendo o vapor úmido das bacias de sabão, transformando a estalagem num formigueiro humano indomável."""
            )
        )

        return Pair(book, chapters)
    }
}
