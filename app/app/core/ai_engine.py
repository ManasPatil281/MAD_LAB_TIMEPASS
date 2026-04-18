import os
import logging
from dotenv import load_dotenv

# Load `.env` into environment if available
load_dotenv()

from langchain_groq import ChatGroq
from langchain_community.document_loaders import PyPDFLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import FAISS
from langchain_huggingface import HuggingFaceEndpointEmbeddings         # replaces HuggingFaceEndpointEmbeddings
from langchain_core.prompts import ChatPromptTemplate
from langchain.chains.combine_documents import create_stuff_documents_chain
from langchain.chains import create_retrieval_chain


logger = logging.getLogger(__name__)
logger.setLevel(logging.WARNING)


embeddings = None
HF_TOKEN = os.getenv("HF_TOKEN", "")


def initialize_embeddings():
    global embeddings

    # ── 1. HuggingFace local embeddings (no API token required) ──────────────
    try:
        model = "sentence-transformers/all-mpnet-base-v2"
        embeddings = HuggingFaceEndpointEmbeddings(
            model=model,
            task="feature-extraction",
            huggingfacehub_api_token=HF_TOKEN,
        )
        return embeddings

    except Exception as e:
        logger.warning(f"Failed to initialize HuggingFace embeddings: {str(e)}")


embeddings = initialize_embeddings()

Groq_API = os.getenv("GROQ_API_KEY", "")

llm = None
llm2 = None

try:
    # llama-3.1-70b-versatile was retired; use 3.3-70b-versatile
    llm  = ChatGroq(groq_api_key=Groq_API, model_name="llama-3.3-70b-versatile")
    # llama3-8b-8192 is the current stable 8 B alias on Groq
    llm2 = ChatGroq(groq_api_key=Groq_API, model_name="openai/gpt-oss-120b")
    pass

except Exception as e:
    logger.error(f"Error initializing models: {str(e)}")


# ── RAG helper ────────────────────────────────────────────────────────────────

def process_pdf_rag(temp_pdf_path: str, system_prompt: str, input_prompt: str, model_type: str = "fast") -> str:
    """Load a PDF, chunk it, embed it, and query via RAG."""
    if not embeddings:
        raise ValueError("Embeddings service is unavailable.")

    # Select model
    target_llm = llm if model_type == "advanced" else llm2


    loader = PyPDFLoader(temp_pdf_path)
    docs = loader.load()

    text_splitter = RecursiveCharacterTextSplitter(chunk_size=5000, chunk_overlap=500)
    splits = text_splitter.split_documents(docs)

    vectorstore = FAISS.from_documents(splits, embeddings)
    retriever = vect"orstore.as_retriever()

    qa_prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", "{context}\n{input}"),
    ])

    question_answer_chain = create_stuff_documents_chain(target_llm, qa_prompt)

    rag_chain = create_retrieval_chain(retriever, question_answer_chain)

    response = rag_chain.invoke({"input": input_prompt})
    return response["answer"]


# ── Task functions ────────────────────────────────────────────────────────────

def ai_detect_text(text: str, model_type: str = "fast") -> str:
    system_prompt = (
        f"Analyze the following text: '{text}' to determine whether it is AI generated. "
        "Provide a percentage score estimating the AI-generated content. "
        "Explain your reasoning based on linguistic patterns, coherence, perplexity, and metadata analysis."
    )
    target_llm = llm if model_type == "advanced" else llm2
    return target_llm.invoke(system_prompt).content



def ai_detect_pdf(temp_pdf_path: str, model_type: str = "fast") -> str:
    system_prompt = (
        "Analyze the provided text or file to determine whether it is AI-generated. "
        "Provide a detailed assessment with a percentage score indicating the estimated proportion of AI-generated content. "
        "Use linguistic patterns, coherence analysis, perplexity, burstiness, and metadata analysis to improve accuracy. "
        "Clearly explain the reasoning behind the AI-generated score with supporting evidence from the text."
    )
    return process_pdf_rag(temp_pdf_path, system_prompt, "Detect if the content is AI-generated.", model_type)



def grammar_check_text(text: str, model_type: str = "fast") -> str:
    system_prompt = (
        f"Perform an advanced grammar and style analysis of the following text: '{text}'. "
        "Identify and correct errors related to spelling, punctuation, sentence structure, verb agreement, and word choice. "
        "Enhance clarity, coherence, and overall readability while preserving the original meaning. "
        "Ensure the text adheres to the highest linguistic standards for the language. "
        "Compare the final output to industry-leading tools like Grammarly, QuillBot and Turnitin. "
        "Provide a final score out of 100, along with suggestions for further refinement."
    )
    target_llm = llm if model_type == "advanced" else llm2
    return target_llm.invoke(system_prompt).content



def paraphrase_text(text: str, language: str, model_type: str = "fast") -> str:
    system_prompt = (
        f"Rephrase the following text: '{text}' to improve readability, clarity and style. "
        f"Ensure that the revised text is in {language}. "
        "Maintain the original meaning while refining grammar, sentence flow, and tone. "
        "Give the output in two parts: original text and paraphrased text. "
        "Use precise word choices and avoid unnecessary complexity to ensure an engaging and well-structured output."
    )
    target_llm = llm if model_type == "advanced" else llm2
    return target_llm.invoke(system_prompt).content



def paraphrase_pdf(temp_pdf_path: str, language: str, model_type: str = "fast") -> str:
    system_prompt = (
        f"Rephrase the following text to improve readability, clarity and style in the {language} language. "
        "Maintain the original meaning while refining grammar, sentence flow, and tone. "
        "Use precise word choices and avoid unnecessary complexity to ensure an engaging and well-structured output."
    )
    return process_pdf_rag(temp_pdf_path, system_prompt, "Paraphrase the text.", model_type)



def detect_plagiarism_pdf(temp_pdf_path: str, model_type: str = "fast") -> str:
    system_prompt = (
        "Perform a thorough plagiarism analysis on the provided text and generate a detailed report similar to Turnitin or Quillbot Premium. "
        "The report should include:\n"
        "1. **Overall Plagiarism Score**: Percentage of detected plagiarism.\n"
        "2. **Section-wise Analysis**: Identify specific parts of the text that match external sources, along with percentage similarity.\n"
        "3. **Source Matching**: List external sources (with URLs) where matching content was found.\n"
        "4. **Highlighted Plagiarized Text**: Display flagged sentences and phrases.\n"
        "5. **Paraphrasing Suggestions**: Provide rewritten versions of plagiarized sections to improve originality.\n"
        "6. **Original Content Summary**: Identify areas that are unique and free from plagiarism.\n"
        "7. **Formatting**: Use code blocks, tables, and structured formatting to enhance readability.\n"
        "8. **Final Report**: Include an overall summary and recommendations for improvement.\n"
        "9. Make the report as long and detailed as possible."
    )
    return process_pdf_rag(temp_pdf_path, system_prompt, "Detect plagiarism and create a detailed report.", model_type)



def summarize_text(text: str, language: str, model_type: str = "fast") -> str:
    system_prompt = (
        f"Summarize the following text: '{text}' while ensuring enhanced readability, clarity, and conciseness. "
        f"Generate the summary in the {language} language. "
        "Preserve all key points while eliminating redundancy and improving coherence. "
        "Ensure the summary remains engaging and contextually accurate."
    )
    target_llm = llm if model_type == "advanced" else llm2
    return target_llm.invoke(system_prompt).content



def summarize_pdf(temp_pdf_path: str, language: str, model_type: str = "fast") -> str:
    system_prompt = (
        "Summarize the given text to improve readability, coherence, and clarity. "
        f"Ensure the output is in {language}. "
        "Maintain the original intent while optimizing the structure for better comprehension and engagement."
    )
    return process_pdf_rag(temp_pdf_path, system_prompt, "Summarize the text.", model_type)



def review_pdf(temp_pdf_path: str, model_type: str = "fast") -> str:
    system_prompt = (
        "Conduct an in-depth review of the provided research paper and generate a comprehensive evaluation report. "
        "The report should include:\n\n"
        "1. **Abstract Analysis**\n"
        "2. **Introduction & Objectives**\n"
        "3. **Literature Review**\n"
        "4. **Methodology Assessment**\n"
        "5. **Results & Data Analysis**\n"
        "6. **Discussion & Conclusion**\n"
        "7. **Citations & References**\n"
        "8. **Plagiarism Analysis**\n"
        "9. **Suggestions for Improvement**\n"
        "10. **Strengths & Unique Contributions**\n"
        "11. **Formatting & Presentation**\n"
        "12. **Overall Summary & Final Score**\n\n"
        "Use code blocks for key highlights, structured tables for clarity, and ensure a downloadable review report is available."
    )
    return process_pdf_rag(
        temp_pdf_path,
        system_prompt,
        "Review the provided research paper and create a detailed report that evaluates its quality, originality, and adherence to academic standards.",
        model_type
    )